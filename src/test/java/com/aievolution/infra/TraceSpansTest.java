package com.aievolution.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;

/** {@link TraceSpans}：span 生命周期样板——命名/打标/记异常/收尾，无 Tracer 时直执。 */
class TraceSpansTest {

  private final Tracer tracer = mock(Tracer.class);

  @SuppressWarnings("unchecked")
  private final ObjectProvider<Tracer> provider = mock(ObjectProvider.class);

  private final TraceSpans spans = new TraceSpans(provider);

  @Test
  void executesBodyDirectlyWhenNoTracer() throws Throwable {
    when(provider.getIfAvailable()).thenReturn(null);

    assertThat(spans.traced("test.op", () -> "ok", (s, r) -> {})).isEqualTo("ok");

    verifyNoInteractions(tracer);
  }

  @Test
  void createsSpanAppliesAttributesAndEnds() throws Throwable {
    Span span = mock(Span.class);
    when(provider.getIfAvailable()).thenReturn(tracer);
    when(tracer.nextSpan()).thenReturn(span);
    when(span.name("test.op")).thenReturn(span);
    when(span.start()).thenReturn(span);
    Tracer.SpanInScope scope = mock(Tracer.SpanInScope.class);
    when(tracer.withSpan(span)).thenReturn(scope);

    String result = spans.traced("test.op", () -> "ok", (s, r) -> s.tag("result", r));

    assertThat(result).isEqualTo("ok");
    InOrder order = inOrder(span, scope);
    order.verify(span).name("test.op");
    order.verify(span).start();
    order.verify(span).tag("result", "ok");
    order.verify(span).end();
  }

  @Test
  void recordsErrorOnSpanAndRethrows() {
    Span span = mock(Span.class);
    when(provider.getIfAvailable()).thenReturn(tracer);
    when(tracer.nextSpan()).thenReturn(span);
    when(span.name("test.op")).thenReturn(span);
    when(span.start()).thenReturn(span);
    when(tracer.withSpan(span)).thenReturn(mock(Tracer.SpanInScope.class));
    RuntimeException boom = new RuntimeException("boom");

    assertThatThrownBy(
            () ->
                spans.traced(
                    "test.op",
                    () -> {
                      throw boom;
                    },
                    (s, r) -> {}))
        .isSameAs(boom);

    verify(span).error(boom);
    verify(span).end();
  }
}
