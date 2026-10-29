package com.aievolution.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** {@link TraceIdSpanTagger}：业务 traceId 挂 span 属性，缺号/无 Tracer/无当前 span 时静默跳过。 */
class TraceIdSpanTaggerTest {

  private final Tracer tracer = mock(Tracer.class);

  @SuppressWarnings("unchecked")
  private final ObjectProvider<Tracer> provider = mock(ObjectProvider.class);

  private final TraceIdSpanTagger tagger = new TraceIdSpanTagger(provider);

  @Test
  void tagsCurrentSpanWithBusinessTraceId() throws Exception {
    when(provider.getIfAvailable()).thenReturn(tracer);
    Span span = mock(Span.class);
    when(tracer.currentSpan()).thenReturn(span);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute(TraceIdFilter.REQUEST_ATTR, "abc12345");

    assertThat(tagger.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();

    verify(span).tag(TraceIdSpanTagger.SPAN_ATTR, "abc12345");
  }

  @Test
  void skipsWhenNoBusinessTraceId() throws Exception {
    assertThat(
            tagger.preHandle(
                new MockHttpServletRequest(), new MockHttpServletResponse(), new Object()))
        .isTrue();

    verifyNoInteractions(tracer);
  }

  @Test
  void skipsWhenNoTracer() throws Exception {
    when(provider.getIfAvailable()).thenReturn(null);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute(TraceIdFilter.REQUEST_ATTR, "abc12345");

    assertThat(tagger.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
  }

  @Test
  void skipsWhenNoCurrentSpan() throws Exception {
    when(provider.getIfAvailable()).thenReturn(tracer);
    when(tracer.currentSpan()).thenReturn(null);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute(TraceIdFilter.REQUEST_ATTR, "abc12345");

    assertThat(tagger.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
  }
}
