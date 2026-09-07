package com.aievolution.infra;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** TraceId 过滤器测试：生成/透传/回写/清理四个语义缺一不可（线程复用场景下 MDC 泄漏是经典事故）。 */
class TraceIdFilterTest {

  private final TraceIdFilter filter = new TraceIdFilter();

  @Test
  void generatesTraceIdWhenAbsentAndEchoesInResponse() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    String echoed = response.getHeader(TraceIdFilter.TRACE_ID_HEADER);
    assertThat(echoed).isNotBlank();
    // 请求处理结束后 MDC 必须清理，防止线程池复用导致串号
    assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
  }

  @Test
  void propagatesIncomingTraceId() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "upstream-trace-123");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER)).isEqualTo("upstream-trace-123");
  }

  @Test
  void rejectsMalformedIncomingTraceId() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    // 日志注入防护：带换行的 traceId 不得透传进日志（伪造日志行攻击）
    request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "evil\n[ERROR] forged log line");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    String echoed = response.getHeader(TraceIdFilter.TRACE_ID_HEADER);
    assertThat(echoed)
        .isNotBlank()
        .doesNotContain("\n")
        .isNotEqualTo("evil\n[ERROR] forged log line");
  }
}
