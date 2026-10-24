package com.aievolution.loop;

import com.aievolution.infra.LogSummaries;
import com.aievolution.prompt.PromptLibrary;
import io.micrometer.core.instrument.MeterRegistry;
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
  private final MeterRegistry meterRegistry;
  private final String systemPrompt;
  private final String synthesisPrompt;
  private final List<Message> conversationHistory;

  public DeepSeekResearchModel(
      ChatClient.Builder chatClientBuilder,
      PromptLibrary promptLibrary,
      ToolRegistry registry,
      String promptName,
      String synthesisPromptName,
      ReactProtocolParser parser,
      MeterRegistry meterRegistry,
      List<Message> conversationHistory) {
    this(
        chatClientBuilder,
        promptLibrary,
        registry.specs(),
        promptName,
        synthesisPromptName,
        parser,
        meterRegistry,
        conversationHistory);
  }

  /**
   * 显式手册构造（W22 #1c）：工具手册条目直接传入——Builder 按通路白名单收窄面板时， 手册与执行白名单同源由调用方保证（{@code
   * ChannelToolExecutor.filteredSpecs}）。
   */
  public DeepSeekResearchModel(
      ChatClient.Builder chatClientBuilder,
      PromptLibrary promptLibrary,
      List<ToolRegistry.ToolSpec> manualSpecs,
      String promptName,
      String synthesisPromptName,
      ReactProtocolParser parser,
      MeterRegistry meterRegistry,
      List<Message> conversationHistory) {
    if (!promptLibrary.exists(promptName)) {
      // prompt 缺失是部署事故，启动即 fail-fast（AgentChatService 先例）
      throw new IllegalStateException("ReAct prompt 模板不存在: " + promptName);
    }
    if (!promptLibrary.exists(synthesisPromptName)) {
      throw new IllegalStateException("ReAct 合成 prompt 模板不存在: " + synthesisPromptName);
    }
    // 裸客户端：不挂工具（工具由 Loop 显式调度）、不挂记忆 advisor
    // ——记忆由调用方组装进构造入参，防 advisor 逐步重复追加同一条 user 消息
    this.chatClient = chatClientBuilder.build();
    this.parser = parser;
    this.meterRegistry = meterRegistry;
    this.systemPrompt =
        promptLibrary.get(promptName).replace(TOOL_MANUAL_PLACEHOLDER, renderManual(manualSpecs));
    this.synthesisPrompt = promptLibrary.get(synthesisPromptName);
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
              // + 可计量（W20 #1 子项 4）：WARN 之外加计数器——降解频率是协议纪律健康度指标，可告警
              meterRegistry.counter("react.protocol.violation.total").increment();
              log.warn(
                  "protocol violation, degrade to direct answer: {}", LogSummaries.summarize(raw));
              return new ModelTurn.Final("", raw);
            });
  }

  @Override
  public java.util.Optional<String> synthesize(String question, List<LoopStep> history) {
    // 裁决①B（W20 #1）：合成步不挂工具手册，只喂问题 + 已查证 observations，诚实红线强制收尾
    String raw =
        chatClient
            .prompt()
            .system(synthesisPrompt)
            .user(buildSynthesisMessage(question, history))
            .call()
            .content();
    if (raw == null || raw.isBlank()) {
      // 合成失败退兜底文案：诚实红线优先于硬凑答案
      log.warn("synthesis step returned blank content, degrade to fallback answer");
      return java.util.Optional.empty();
    }
    return java.util.Optional.of(raw);
  }

  private String buildSynthesisMessage(String question, List<LoopStep> history) {
    StringBuilder sb = new StringBuilder();
    appendConversationHistory(sb);
    sb.append("【用户问题】\n").append(question).append("\n\n【已完成的查证】\n");
    if (history.isEmpty()) {
      sb.append("尚无\n");
    } else {
      history.forEach(
          s ->
              sb.append(
                  "步骤 %d：调用 %s(%s)\n结果：%s\n"
                      .formatted(s.index() + 1, s.tool(), s.input(), s.observation())));
    }
    sb.append("\n请基于以上查证直接作答。");
    return sb.toString();
  }

  private String buildUserMessage(String question, List<LoopStep> history) {
    StringBuilder sb = new StringBuilder();
    appendConversationHistory(sb);
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

  /** 会话历史注入（单点：nextTurn 与 synthesize 共用——A11 两次即收口） */
  private void appendConversationHistory(StringBuilder sb) {
    if (conversationHistory.isEmpty()) {
      return;
    }
    sb.append("【会话历史】\n");
    conversationHistory.forEach(
        m ->
            sb.append(m.getMessageType().getValue()).append(": ").append(m.getText()).append('\n'));
    sb.append('\n');
  }

  private static String renderManual(List<ToolRegistry.ToolSpec> specs) {
    return specs.stream()
        .map(s -> "- %s：%s。入参 JSON：%s".formatted(s.name(), s.description(), s.paramsSchema()))
        .collect(Collectors.joining("\n"));
  }
}
