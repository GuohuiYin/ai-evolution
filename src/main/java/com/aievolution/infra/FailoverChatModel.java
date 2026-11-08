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
 *
 * <p>冷却窗短路（W31 #3 窗口实测修订裁决 A→B）：切换发生后冷却窗内**直连备用臂**，不再先试主力—— W31 值守窗口实账：Spring AI 重试模板对连接拒绝重试 40+
 * 次（约 90 秒/次调用）， 冷却窗内逐调用先试主力的语义把故障税放大一个量级。 窗口过后恢复先试主力（一次自然探测：通则回主力，断则重进冷却窗）。
 */
public class FailoverChatModel implements ChatModel, ModelArmRegistry {

  private static final Logger log = LoggerFactory.getLogger(FailoverChatModel.class);

  private final ChatModel primary;
  private final ChatModel fallback;
  private final MeterRegistry meterRegistry;
  private final String fallbackName;
  private final java.time.Duration armCooldown;
  private final java.time.Clock clock;

  /** 最近一次切换时刻（null=从未切换）；冷却窗判定依据，W31 #1 */
  private volatile java.time.Instant lastFailoverAt;

  public FailoverChatModel(
      ChatModel primary,
      ChatModel fallback,
      MeterRegistry meterRegistry,
      String fallbackName,
      java.time.Duration armCooldown,
      java.time.Clock clock) {
    this.primary = primary;
    this.fallback = fallback;
    this.meterRegistry = meterRegistry;
    this.fallbackName = fallbackName;
    this.armCooldown = armCooldown;
    this.clock = clock;
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
    if (inCooldown()) {
      // 冷却窗内直连备用臂（W31 裁决修订）：省掉对死主力的重试风暴（实测约 90s/次调用）
      return fallback.call(toFallbackPrompt(prompt));
    }
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
    if (inCooldown()) {
      return Flux.defer(() -> fallback.stream(toFallbackPrompt(prompt)));
    }
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

  /**
   * 活跃臂判定（W31 #3 窗口实测修订裁决 A→B，路径 B）：切换发生后冷却窗内报备用臂且调用路径直连备用臂 （见 call/stream 的 {@link #inCooldown()}
   * 短路），窗口过后恢复先试主力（一次自然探测：通则回主力，断则重进冷却窗）。 编排层据臂查超时预算档实现「换臂即换档」。
   */
  @Override
  public String activeArm() {
    return inCooldown() ? ModelArmRegistry.FALLBACK : ModelArmRegistry.PRIMARY;
  }

  /** 冷却窗判定单点：最近一次切换时刻 + 臂冷却时长。call/stream/activeArm 共用， 判定逻辑只允许这一处副本（A11-1 两次即收口）。 */
  private boolean inCooldown() {
    java.time.Instant last = this.lastFailoverAt;
    return last != null && this.clock.instant().isBefore(last.plus(this.armCooldown));
  }

  private void alert(Throwable error) {
    this.lastFailoverAt = this.clock.instant();
    meterRegistry.counter("model.failover.total", "fallback", fallbackName).increment();
    log.warn(
        "主力模型调用失败，自动切换备用模型 fallback={} errorType={} msg={}",
        fallbackName,
        error.getClass().getSimpleName(),
        error.getMessage());
  }
}
