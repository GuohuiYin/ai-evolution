package com.aievolution.loop.pipeline;

import com.aievolution.cost.CostChannel;
import com.aievolution.infra.LogSummaries;
import com.aievolution.prompt.PromptLibrary;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;

/**
 * Planner 的模型适配（W22 #1b）：把 {@link Planner} 端口接到主力档模型 （W22 #1a 定档 deepseek-v4-flash：10/10 质量可用，均延迟
 * 1.77s）。
 *
 * <p>消息口径与定档实验脚本一致：prompt 模板（含会话历史与用户问题） 渲染后作为单条 user 消息发出，无 system 消息、无工具。
 *
 * <p>失败语义（设计 §1.3）：输出不可解析 / 空子问题 / 枚举外通路 → 降级为 {@link Plan#fallback} 单子问题保底，不阻塞链路；降级走 WARN +
 * 计数器可计量（react.protocol.violation.total 同款纪律）。
 */
public class DeepSeekPlanner implements Planner {

  private static final Logger log = LoggerFactory.getLogger(DeepSeekPlanner.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final ChatClient chatClient;
  private final MeterRegistry meterRegistry;
  private final String promptTemplate;

  public DeepSeekPlanner(
      ChatClient.Builder chatClientBuilder,
      PromptLibrary promptLibrary,
      String promptName,
      MeterRegistry meterRegistry) {
    if (!promptLibrary.exists(promptName)) {
      // prompt 缺失是部署事故，启动即 fail-fast（DeepSeekResearchModel 先例）
      throw new IllegalStateException("Planner prompt 模板不存在: " + promptName);
    }
    // 裸客户端：Planner 无工具（纯规划，设计 §1.3）、不挂记忆 advisor——历史由入参显式渲染
    // W25 #2a-ii 通路标记：语句式而非链式挂载（理由见 RagChatService）
    chatClientBuilder.defaultAdvisors(a -> a.param(CostChannel.CONTEXT_KEY, CostChannel.PIPELINE));
    this.chatClient = chatClientBuilder.build();
    this.promptTemplate = promptLibrary.get(promptName);
    this.meterRegistry = meterRegistry;
  }

  @Override
  public Plan plan(String question, List<Message> history) {
    String rendered =
        this.promptTemplate
            .replace("{history}", renderHistory(history))
            .replace("{query}", question);
    String raw = this.chatClient.prompt().user(rendered).call().content();
    return parseOrFallback(raw, question);
  }

  private Plan parseOrFallback(String raw, String question) {
    String json = extractJson(raw);
    if (json != null) {
      try {
        Plan plan = sanitize(MAPPER.readValue(json, Plan.class));
        if (plan != null) {
          return plan;
        }
      } catch (Exception e) {
        log.warn("planner output unparseable: {}", LogSummaries.summarize(raw));
      }
    } else {
      log.warn("planner output has no JSON: {}", LogSummaries.summarize(raw));
    }
    this.meterRegistry.counter("planner.parse.fallback.total").increment();
    return Plan.fallback(question);
  }

  /** 清洗模型输出：丢空白子问题、缺省通路补 rag；清洗后为空视同不可解析（返回 null） */
  private static Plan sanitize(Plan plan) {
    if (plan == null || plan.subQuestions() == null) {
      return null;
    }
    List<Plan.SubQuestion> cleaned =
        plan.subQuestions().stream()
            .filter(sq -> sq.q() != null && !sq.q().isBlank())
            .map(sq -> sq.channel() == null ? new Plan.SubQuestion(sq.q(), Channel.RAG) : sq)
            .toList();
    return cleaned.isEmpty() ? null : new Plan(cleaned);
  }

  /** 容错提取：模型输出可能带前导空行 / markdown 代码围栏（Qwen 臂实测），取首个 { 到末个 } */
  private static String extractJson(String raw) {
    if (raw == null) {
      return null;
    }
    int start = raw.indexOf('{');
    int end = raw.lastIndexOf('}');
    return start >= 0 && end > start ? raw.substring(start, end + 1) : null;
  }

  private static String renderHistory(List<Message> history) {
    if (history == null || history.isEmpty()) {
      return "（无）";
    }
    StringBuilder sb = new StringBuilder();
    history.forEach(
        m ->
            sb.append(m.getMessageType().getValue()).append(": ").append(m.getText()).append('\n'));
    return sb.toString().stripTrailing();
  }
}
