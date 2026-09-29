package com.aievolution.rag;

import com.aievolution.prompt.PromptLibrary;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * DeepSeek 查询改写（W11 #5，默认档）：把提问 + 会话历史发给模型，产出自足检索查询。
 *
 * <p>W15 #2（A1 裁决）契约变更：首轮直通废除——语料指代（"这两家/谁"指语料实体而非对话历史） 证伪"首轮查询必自足"假设，首轮同样过模型；自足查询由 prompt
 * 约定原样返回，成本实测入账。
 *
 * <p>失败兜底：改写失败（空白输出）返回原 query——宁可指代未消解，也不拿空串去向量化。
 */
@Component
@ConditionalOnProperty(name = "ai.rewrite.enabled", havingValue = "true", matchIfMissing = true)
public class DeepSeekQueryRewriter implements QueryRewriter {

  private static final Logger log = LoggerFactory.getLogger(DeepSeekQueryRewriter.class);

  private final ChatClient chatClient;
  private final String systemPrompt;

  public DeepSeekQueryRewriter(
      ChatClient.Builder chatClientBuilder,
      PromptLibrary promptLibrary,
      @Value("${ai.rag.rewrite-prompt:query-rewrite-v1}") String promptName,
      @Value("${ai.rag.rewrite-temperature:0.0}") double rewriteTemperature) {
    if (!promptLibrary.exists(promptName)) {
      // prompt 缺失是部署事故，启动即 fail-fast
      throw new IllegalStateException("查询改写 prompt 模板不存在: " + promptName);
    }
    // 裸客户端：改写是单次无状态调用，不挂记忆 advisor（历史由入参显式携带）；
    // 温度策略外置（ai.rag.rewrite-temperature，默认 0）——改写是确定性任务（指代消解/
    // 格式变换，非创作），低温消除同查询多轮措辞漂移（W15 R3/R4 实测默认温度下检索排序翻转 ±2 条）
    this.chatClient =
        chatClientBuilder
            .defaultOptions(DeepSeekChatOptions.builder().temperature(rewriteTemperature))
            .build();
    this.systemPrompt = promptLibrary.get(promptName);
  }

  @Override
  public String rewrite(String query, List<Message> history) {
    // W15 #2（A1 裁决）：首轮直通废除——语料指代（"这两家/谁"指语料实体而非对话历史）
    // 证伪"首轮查询必自足"假设；自足查询由 prompt 约定原样返回，成本实测入账
    StringBuilder sb = new StringBuilder("【对话历史】\n");
    if (history.isEmpty()) {
      sb.append("（无，用户首轮提问）\n");
    } else {
      history.forEach(
          m ->
              sb.append(m.getMessageType().getValue())
                  .append(": ")
                  .append(m.getText())
                  .append('\n'));
    }
    sb.append("\n【最新提问】\n").append(query);
    String rewritten;
    try {
      rewritten = chatClient.prompt().system(systemPrompt).user(sb.toString()).call().content();
    } catch (RuntimeException e) {
      // A1 把改写变成必经路径后的爆炸半径控制：模型不可用（402/超时）不拖垮检索与对话——
      // 降级为原查询检索（同空白兜底的"宁可指代未消解"哲学），但必须留痕（静默降级是故障放大器）
      log.warn("查询改写降级为原查询 errorType={}", e.getClass().getSimpleName());
      return query;
    }
    return rewritten == null || rewritten.isBlank() ? query : rewritten.strip();
  }
}
