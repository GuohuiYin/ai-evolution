package com.aievolution.rag;

import com.aievolution.prompt.PromptLibrary;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * DeepSeek 查询改写（W11 #5，默认档）：把追问 + 会话历史发给模型，产出自足检索查询。
 *
 * <p>成本纪律：首轮（无历史）直通零调用；改写失败（空白输出）兜底返回原 query—— 宁可指代未消解，也不拿空串去向量化。
 */
@Component
@ConditionalOnProperty(name = "ai.rewrite.enabled", havingValue = "true", matchIfMissing = true)
public class DeepSeekQueryRewriter implements QueryRewriter {

  private final ChatClient chatClient;
  private final String systemPrompt;

  public DeepSeekQueryRewriter(
      ChatClient.Builder chatClientBuilder,
      PromptLibrary promptLibrary,
      @Value("${ai.rag.rewrite-prompt:query-rewrite-v1}") String promptName) {
    if (!promptLibrary.exists(promptName)) {
      // prompt 缺失是部署事故，启动即 fail-fast
      throw new IllegalStateException("查询改写 prompt 模板不存在: " + promptName);
    }
    // 裸客户端：改写是单次无状态调用，不挂记忆 advisor（历史由入参显式携带）
    this.chatClient = chatClientBuilder.build();
    this.systemPrompt = promptLibrary.get(promptName);
  }

  @Override
  public String rewrite(String query, List<Message> history) {
    if (history.isEmpty()) {
      return query; // 首轮零成本直通
    }
    StringBuilder sb = new StringBuilder("【对话历史】\n");
    history.forEach(
        m ->
            sb.append(m.getMessageType().getValue()).append(": ").append(m.getText()).append('\n'));
    sb.append("\n【最新追问】\n").append(query);
    String rewritten =
        chatClient.prompt().system(systemPrompt).user(sb.toString()).call().content();
    return rewritten == null || rewritten.isBlank() ? query : rewritten.strip();
  }
}
