package com.aievolution.loop.pipeline;

import com.aievolution.cost.CostChannel;
import com.aievolution.infra.LogSummaries;
import com.aievolution.prompt.PromptLibrary;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

/**
 * Reviewer 的模型适配（W22 #1c）：主力档模型（复核质量不能省，设计 §1.3）， 复核清单见 {@code prompts/reviewer-v1.md}（溯源/红线/口径三条）。
 *
 * <p>失败语义：输出不可解析 → <b>fail-closed 打回</b>（宁可打回重来， 不可放过未复核草稿）+ 计数器可计量。与 eval judge 不共用 prompt 文件
 * （ADR-0014 隔离：改评估尺不伤在线行为）。
 */
public class DeepSeekReviewer implements Reviewer {

  private static final Logger log = LoggerFactory.getLogger(DeepSeekReviewer.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final ChatClient chatClient;
  private final MeterRegistry meterRegistry;
  private final String promptTemplate;

  public DeepSeekReviewer(
      ChatClient.Builder chatClientBuilder,
      PromptLibrary promptLibrary,
      String promptName,
      MeterRegistry meterRegistry) {
    if (!promptLibrary.exists(promptName)) {
      throw new IllegalStateException("Reviewer prompt 模板不存在: " + promptName);
    }
    // 裸客户端：Reviewer 无工具（只读证据包，设计 §1.3）
    // W25 #2a-ii 通路标记：语句式而非链式挂载（理由见 RagChatService）
    chatClientBuilder.defaultAdvisors(a -> a.param(CostChannel.CONTEXT_KEY, CostChannel.PIPELINE));
    this.chatClient = chatClientBuilder.build();
    this.promptTemplate = promptLibrary.get(promptName);
    this.meterRegistry = meterRegistry;
  }

  @Override
  public ReviewVerdict review(String question, EvidencePack evidence, DraftAnswer draft) {
    String rendered =
        this.promptTemplate
            .replace("{query}", question)
            .replace("{evidence}", renderEvidence(evidence))
            .replace("{draft}", draft.content());
    String raw = this.chatClient.prompt().user(rendered).call().content();
    return parseOrFailClosed(raw);
  }

  private ReviewVerdict parseOrFailClosed(String raw) {
    String json = extractJson(raw);
    if (json != null) {
      try {
        VerdictPayload payload = MAPPER.readValue(json, VerdictPayload.class);
        if (payload.verdict() != null) {
          String kind = payload.verdict().trim().toUpperCase(Locale.ROOT);
          if (ReviewVerdict.PASS.equals(kind)) {
            return ReviewVerdict.pass();
          }
          if (ReviewVerdict.REJECT.equals(kind)) {
            return ReviewVerdict.reject(payload.comment() == null ? "" : payload.comment());
          }
        }
      } catch (Exception e) {
        log.warn("reviewer output unparseable: {}", LogSummaries.summarize(raw));
      }
    } else {
      log.warn("reviewer output has no JSON: {}", LogSummaries.summarize(raw));
    }
    this.meterRegistry.counter("reviewer.parse.fallback.total").increment();
    return ReviewVerdict.reject("复核输出不可解析，打回重写");
  }

  /** 与 DeepSeekPlanner 同款容错：前导空行/代码围栏，取首个 { 到末个 } */
  private static String extractJson(String raw) {
    if (raw == null) {
      return null;
    }
    int start = raw.indexOf('{');
    int end = raw.lastIndexOf('}');
    return start >= 0 && end > start ? raw.substring(start, end + 1) : null;
  }

  private static String renderEvidence(EvidencePack pack) {
    if (pack.items().isEmpty()) {
      return "（无取证记录）";
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < pack.items().size(); i++) {
      EvidencePack.EvidenceItem item = pack.items().get(i);
      sb.append(
          "证据 %d：工具 %s(%s)\n返回：%s\n"
              .formatted(i + 1, item.tool(), item.input(), item.observation()));
    }
    return sb.toString().stripTrailing();
  }

  /** 复核输出的 JSON 载体（模型输出层，与契约记录解耦） */
  private record VerdictPayload(String verdict, String comment) {}
}
