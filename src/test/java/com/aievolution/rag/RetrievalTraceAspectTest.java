package com.aievolution.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aievolution.infra.TraceSpans;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.ObjectProvider;

/** {@link RetrievalTraceAspect}：检索段 span 属性——RETRIEVER 类型 + 查询原文 + 命中数/分数标尺。 */
class RetrievalTraceAspectTest {

  private final Span span = mock(Span.class);

  @SuppressWarnings("unchecked")
  private final ObjectProvider<Tracer> provider = mock(ObjectProvider.class);

  private final RetrievalTraceAspect aspect = new RetrievalTraceAspect(new TraceSpans(provider));

  @BeforeEach
  void wireTracer() {
    Tracer tracer = mock(Tracer.class);
    when(provider.getIfAvailable()).thenReturn(tracer);
    when(tracer.nextSpan()).thenReturn(span);
    when(span.name("rag.retrieval")).thenReturn(span);
    when(span.start()).thenReturn(span);
    when(tracer.withSpan(span)).thenReturn(mock(Tracer.SpanInScope.class));
  }

  @Test
  void tagsEvidenceAttributesForEvidenceEntry() throws Throwable {
    ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
    when(pjp.getArgs()).thenReturn(new Object[] {"茅台分红"});
    when(pjp.proceed())
        .thenReturn(
            new RetrievalEvidence(List.of(mock(Document.class), mock(Document.class)), 0.62));

    Object result = aspect.traceRetrieval(pjp);

    assertThat(result).isInstanceOf(RetrievalEvidence.class);
    verify(span).tag("openinference.span.kind", "RETRIEVER");
    verify(span).tag("input.value", "茅台分红");
    verify(span).tag("rag.hits", "2");
    verify(span).tag("rag.dense_top_score", "0.620");
  }

  @Test
  void tagsHitCountForPlainListEntry() throws Throwable {
    ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
    when(pjp.getArgs()).thenReturn(new Object[] {"年报"});
    when(pjp.proceed()).thenReturn(List.of(mock(Document.class)));

    aspect.traceRetrieval(pjp);

    verify(span).tag("rag.hits", "1");
  }
}
