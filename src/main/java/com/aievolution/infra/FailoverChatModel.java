package com.aievolution.infra;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.web.client.ResourceAccessException;
import reactor.core.publisher.Flux;

/**
 * 模型自动降级装饰器（W12 #4）：主力（DeepSeek）故障时自动切备用（Qwen，SiliconFlow 托管）。
 *
 * <p>切换判据（裁决 2026-09-16，能力地图"降级容错"项）：
 *
 * <ul>
 *   <li><b>切</b>：{@link TransientAiException}（5xx）与 {@link ResourceAccessException}（超时/IO）， 含响应式链路的
 *       cause 包装——供应商侧故障，换供应商有意义
 *   <li><b>不切</b>：{@link NonTransientAiException}（4xx：请求/账户语义问题，换供应商同样失败）—— 原样抛出，避免掩盖真实病灶（如 402
 *       余额不足应直接报警而不是悄悄烧备用额度）
 *   <li><b>流式只在首 token 前允许切换</b>：已吐内容后失败若重放整段会造成重复输出， 中途失败交给 SSE 客户端显式报错
 * </ul>
 *
 * <p>降级必须可见：WARN 告警日志（traceId 串联）+ {@code model.failover.total} 计数器（A6 埋点）—— 静默降级是故障放大器。备用模型经
 * SiliconFlow 共享算力延迟显著偏高（选型实测 30-60x， docs/eval/selection/model-selection-v1.md），降级期用户体验下降属预期代价。
 */
public class FailoverChatModel implements ChatModel {

  private static final Logger log = LoggerFactory.getLogger(FailoverChatModel.class);

  private final ChatModel primary;
  private final ChatModel fallback;
  private final MeterRegistry meterRegistry;
  private final String fallbackName;

  public FailoverChatModel(
      ChatModel primary, ChatModel fallback, MeterRegistry meterRegistry, String fallbackName) {
    this.primary = primary;
    this.fallback = fallback;
    this.meterRegistry = meterRegistry;
    this.fallbackName = fallbackName;
  }

  @Override
  public ChatOptions getOptions() {
    // ChatClient 装配从 getOptions() 取默认选项；接口默认实现返回通用 DefaultChatOptions，
    // 而 DeepSeekChatModel.createRequest 会把 Prompt.options 强转 DeepSeekChatOptions——
    // 不透传主力同型选项则正常路径直接炸（W12 #4c 故障注入实测抓获）
    return primary.getOptions();
  }

  @Override
  public ChatOptions getDefaultOptions() {
    return primary.getDefaultOptions();
  }

  @Override
  public ChatResponse call(Prompt prompt) {
    try {
      return primary.call(prompt);
    } catch (RuntimeException e) {
      if (!isFailoverWorthy(e)) {
        throw e;
      }
      alert(e);
      return fallback.call(toFallbackPrompt(prompt));
    }
  }

  @Override
  public Flux<ChatResponse> stream(Prompt prompt) {
    AtomicBoolean emitted = new AtomicBoolean(false);
    return Flux.defer(() -> primary.stream(prompt))
        .doOnNext(chunk -> emitted.set(true))
        .onErrorResume(
            err -> {
              if (emitted.get() || !isFailoverWorthy(err)) {
                return Flux.error(err);
              }
              alert(err);
              return fallback.stream(toFallbackPrompt(prompt));
            });
  }

  /**
   * 降级路径重建 Prompt：options 换成备用模型的默认选项（携带正确的模型名与供应商专有字段）， 主力专有 options 不可跨供应商复用（双方 createRequest
   * 均强转具体类型）。降级是应急通道， 保可用性优先于保逐请求调参一致性——本工程对话/分析/judge 链路均走默认 options，无实际损失；
   * 若未来引入按请求调参（温度等），需在此补可移植字段映射。
   */
  private Prompt toFallbackPrompt(Prompt prompt) {
    return new Prompt(prompt.getInstructions(), fallback.getDefaultOptions());
  }

  /** 切换判据单点：沿 cause 链识别。先排 4xx（包装层在上游时必须优先命中"不切"）， 再认 5xx/超时。两层语义冲突时宁可不切——误切掩盖病灶，误不切只是维持现状报错。 */
  static boolean isFailoverWorthy(Throwable error) {
    for (Throwable cur = error; cur != null; cur = cur.getCause()) {
      if (cur instanceof NonTransientAiException) {
        return false;
      }
      if (cur instanceof TransientAiException || cur instanceof ResourceAccessException) {
        return true;
      }
    }
    return false;
  }

  private void alert(Throwable error) {
    meterRegistry.counter("model.failover.total", "fallback", fallbackName).increment();
    log.warn(
        "主力模型调用失败，自动切换备用模型 fallback={} errorType={} msg={}",
        fallbackName,
        error.getClass().getSimpleName(),
        error.getMessage());
  }
}
