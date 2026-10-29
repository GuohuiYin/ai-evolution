package com.aievolution.infra;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

/**
 * W3C traceparent 级别的链路上下文交接（W25 #1a 收尾）：跨线程/跨进程边界（Temporal worker、 弹性线程池）处，把「当前 span 是谁」压缩成一串
 * traceparent 文本带过去，对端再还原成远端父级 （remote parent）装入当前上下文——之后产生的 span 自动挂回原 trace，不再孤儿化。
 *
 * <p>与 {@link TraceSpans} 同一约定：传播/还原失败一律降级为「无父级直执」，观测缺口永不击穿业务。
 */
public final class TraceContextPropagation {

  /** 无操作作用域：入参非法时的统一返回，调用方 try-with-resources 形态不变 */
  public static final Scope NOOP = () -> {};

  private TraceContextPropagation() {}

  /** 当前线程的 traceparent（W3C 格式 {@code 00-<traceId>-<spanId>-<flags>}）；无有效 span 时为 null */
  public static String currentTraceparent() {
    SpanContext sc = Span.current().getSpanContext();
    if (!sc.isValid()) {
      return null;
    }
    return "00-%s-%s-%s".formatted(sc.getTraceId(), sc.getSpanId(), sc.getTraceFlags().asHex());
  }

  /** 把 traceparent 还原为远端父级并装入当前上下文；入参非法返回 {@link #NOOP}（无父级直执） */
  public static Scope withRemoteParent(String traceparent) {
    SpanContext parent = parse(traceparent);
    if (parent == null) {
      return NOOP;
    }
    return Context.current().with(Span.wrap(parent)).makeCurrent();
  }

  private static SpanContext parse(String traceparent) {
    if (traceparent == null) {
      return null;
    }
    String[] parts = traceparent.split("-");
    if (parts.length != 4) {
      return null;
    }
    try {
      return SpanContext.createFromRemoteParent(
          parts[1],
          parts[2],
          TraceFlags.fromByte(Byte.parseByte(parts[3], 16)),
          TraceState.getDefault());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
