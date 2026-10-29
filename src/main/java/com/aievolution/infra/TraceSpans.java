package com.aievolution.infra;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.util.Map;
import java.util.function.BiConsumer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 自定义 span 生命周期助手（W25 #1a，ADR-0020）：检索/工具/ReAct 步等业务段的 span 样板（开 span → 作用域 → 打标 → 记异常 →
 * 收尾）单点收口（A11-1），各埋点处只声明 名字与属性，不各自复制 try/finally。
 *
 * <p>无 tracing 环境（Tracer 缺席，如 tracing 关闭）时直执业务体——埋点永不改变业务语义。
 */
@Component
public class TraceSpans {

  /** 可抛业务体：与 AOP {@code ProceedingJoinPoint.proceed()} 的 throws Throwable 形态对齐。 */
  @FunctionalInterface
  public interface TracedCall<T> {
    T call() throws Throwable;
  }

  private final ObjectProvider<Tracer> tracer;

  public TraceSpans(ObjectProvider<Tracer> tracer) {
    this.tracer = tracer;
  }

  /**
   * 在名为 {@code name} 的 span 作用域内执行 {@code body}：成功后由 {@code onSuccess} 打属性， 异常记 span error 并原样抛出。
   */
  public <T> T traced(String name, TracedCall<T> body, BiConsumer<Span, T> onSuccess)
      throws Throwable {
    return traced(name, Map.of(), body, onSuccess);
  }

  /**
   * {@link #traced(String, TracedCall, BiConsumer)} 全参版：{@code baseAttrs} 在执行前打标——
   * 身份类属性（操作类型/工具名等）失败路径也要在 span 上可见，等不到 onSuccess。
   */
  public <T> T traced(
      String name, Map<String, String> baseAttrs, TracedCall<T> body, BiConsumer<Span, T> onSuccess)
      throws Throwable {
    Tracer t = tracer.getIfAvailable();
    if (t == null) {
      return body.call();
    }
    Span span = t.nextSpan().name(name).start();
    baseAttrs.forEach(span::tag);
    try (Tracer.SpanInScope ignored = t.withSpan(span)) {
      T result = body.call();
      onSuccess.accept(span, result);
      return result;
    } catch (Throwable e) {
      span.error(e);
      throw e;
    } finally {
      span.end();
    }
  }
}
