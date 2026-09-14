package com.aievolution.loop;

import com.aievolution.infra.LogSummaries;
import com.aievolution.prompt.PromptLibrary;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;

/**
 * DeepSeek 研究模型适配（W11 #3b-2）：把 {@link ResearchModel} 端口接到真实模型上。
 *
 * <p>与旧通路的分工：旧通路把工具交给 Spring AI 内部循环（隐式）；本适配不挂任何工具给模型， 只给协议与工具手册（{@link ToolRegistry#specs()} 渲染进系统
 * prompt）， 模型的每步决策经 {@link ReactProtocolParser} 解析后由 {@link ResearchLoop} 显式驱动。
 *
 * <p>降级策略：模型输出不合协议时直通兜底——原文作为终答返回（不丢内容）； 协议遵从度不靠重试硬扳，靠 prompt 迭代与黄金集 eval 度量（A13）。
 */
public class DeepSeekResearchModel implements ResearchModel {

  private static final Logger log = LoggerFactory.getLogger(DeepSeekResearchModel.class);
  private static final String TOOL_MANUAL_PLACEHOLDER = "{TOOL_MANUAL}";

  private final ChatClient chatClient;
  private final ReactProtocolParser parser;
  private final String systemPrompt;
  private final List<Message> conversationHistory;

  public DeepSeekResearchModel(
      ChatClient.Builder chatClientBuilder,
      PromptLibrary promptLibrary,
      ToolRegistry registry,
      String promptName,
      ReactProtocolParser parser,
      List<Message> conversationHistory) {
    if (!promptLibrary.exists(promptName)) {
      // prompt 缺失是部署事故，启动即 fail-fast（AgentChatService 先例）
      throw new IllegalStateException("ReAct prompt 模板不存在: " + promptName);
    }
    // 裸客户端：不挂工具（工具由 Loop 显式调度）、不挂记忆 advisor
    // ——记忆由调用方组装进构造入参，防 advisor 逐步重复追加同一条 user 消息
    this.chatClient = chatClientBuilder.build();
    this.parser = parser;
    this.systemPrompt =
        promptLibrary.get(promptName).replace(TOOL_MANUAL_PLACEHOLDER, renderManual(registry));
    this.conversationHistory = conversationHistory;
  }

  @Override
  public ModelTurn nextTurn(String question, List<LoopStep> history) {
    String raw =
        chatClient
            .prompt()
            .system(systemPrompt)
            .user(buildUserMessage(question, history))
            .call()
            .content();
    return parser
        .parse(raw)
        .orElseGet(
            () -> {
              // 协议违背必须可见（W11 #3c 教训：静默直通曾把协议原文当答案，首轮 eval 三案归零才发现）
              log.warn(
                  "protocol violation, degrade to direct answer: {}", LogSummaries.summarize(raw));
              return new ModelTurn.Final("", raw);
            });
  }

  private String buildUserMessage(String question, List<LoopStep> history) {
    StringBuilder sb = new StringBuilder();
    if (!conversationHistory.isEmpty()) {
      sb.append("【会话历史】\n");
      conversationHistory.forEach(
          m ->
              sb.append(m.getMessageType().getValue())
                  .append(": ")
                  .append(m.getText())
                  .append('\n'));
      sb.append('\n');
    }
    sb.append("【当前问题】\n").append(question).append("\n\n【已完成的研究步骤】\n");
    if (history.isEmpty()) {
      sb.append("尚无\n");
    } else {
      history.forEach(
          s ->
              sb.append(
                  "步骤 %d\nThought: %s\nAction: %s(%s)\nObservation: %s\n"
                      .formatted(
                          s.index() + 1, s.thought(), s.tool(), s.input(), s.observation())));
    }
    sb.append("\n请严格按协议输出下一步。");
    return sb.toString();
  }

  private static String renderManual(ToolRegistry registry) {
    return registry.specs().stream()
        .map(s -> "- %s：%s。入参 JSON：%s".formatted(s.name(), s.description(), s.paramsSchema()))
        .collect(Collectors.joining("\n"));
  }
}
