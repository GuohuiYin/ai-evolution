package com.aievolution.loop.temporal;

import com.aievolution.infra.TraceContextPropagation;
import io.temporal.api.common.v1.Payload;
import io.temporal.common.context.ContextPropagator;
import io.temporal.common.converter.DataConverter;
import java.util.Collections;
import java.util.Map;

/**
 * OTel 链路上下文的 Temporal 传播器（W25 #1a 收尾，ADR-0020）：client 侧把当前 span 的 traceparent 写进 workflow/activity
 * header，worker 侧由 SDK 回调存入 ThreadLocal， 供 {@link TraceContextWorkerInterceptor} 在 activity
 * 执行期还原——activity 内产生的 span （chat_client / tool.* / react.step）挂回请求 trace，不再沦为孤儿 root。
 *
 * <p>传播失败一律降级为「无父级」（{@link TraceContextPropagation} 同款约定），观测缺口永不击穿业务。
 */
public class TraceContextPropagator implements ContextPropagator {

  private static final String KEY = "traceparent";
  private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

  /** worker 线程当前交接到的 traceparent（无则 null）——activity 拦截器的读取点 */
  public static String currentTraceparent() {
    return CURRENT.get();
  }

  /** activity 执行完毕由拦截器清线程副本——worker 线程池复用不串号 */
  public static void clearCurrent() {
    CURRENT.remove();
  }

  @Override
  public String getName() {
    return "otel-trace-context";
  }

  @Override
  public Object getCurrentContext() {
    // workflow 线程：取启动时交接的值再往下游 activity 传；client 线程：现场从 OTel 当前 span 捕获
    String propagated = CURRENT.get();
    return propagated != null ? propagated : TraceContextPropagation.currentTraceparent();
  }

  @Override
  public void setCurrentContext(Object context) {
    if (context instanceof String s) {
      CURRENT.set(s);
    } else {
      CURRENT.remove();
    }
  }

  @Override
  public Map<String, Payload> serializeContext(Object context) {
    if (!(context instanceof String s)) {
      return Collections.emptyMap();
    }
    return DataConverter.getDefaultInstance()
        .toPayload(s)
        .map(p -> Map.of(KEY, p))
        .orElseGet(Collections::emptyMap);
  }

  @Override
  public Object deserializeContext(Map<String, Payload> context) {
    Payload p = context.get(KEY);
    if (p == null) {
      return null;
    }
    try {
      return DataConverter.getDefaultInstance().fromPayload(p, String.class, String.class);
    } catch (RuntimeException e) {
      return null;
    }
  }
}
