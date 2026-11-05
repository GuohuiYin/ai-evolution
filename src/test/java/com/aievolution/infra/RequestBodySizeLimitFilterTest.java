package com.aievolution.infra;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** W28 #3 请求体上限过滤器：Content-Length 超上限 → 413 ProblemDetail，不进入限流/鉴权/业务链。 */
class RequestBodySizeLimitFilterTest {

  private final RequestBodySizeLimitFilter filter = new RequestBodySizeLimitFilter(1024);

  @Test
  void underLimitPassesThrough() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest("POST", "/ai/chat");
    req.setContent(new byte[1000]);
    MockHttpServletResponse res = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(req, res, chain);

    assertThat(chain.getRequest()).isNotNull(); // 链放行
    assertThat(res.getStatus()).isEqualTo(200);
  }

  @Test
  void overLimitRejected413WithProblemDetail() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest("POST", "/ai/chat");
    req.setContent(new byte[2048]);
    MockHttpServletResponse res = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(req, res, chain);

    assertThat(chain.getRequest()).isNull(); // 未进入后续链
    assertThat(res.getStatus()).isEqualTo(413);
    assertThat(res.getContentType()).contains("application/problem+json");
    assertThat(res.getContentAsString()).contains("request_too_large");
  }

  @Test
  void unknownContentLengthPassesThrough() throws Exception {
    // 无 Content-Length（chunked 等）：不在本层拦，由业务校验（@Size）兜底
    MockHttpServletRequest req = new MockHttpServletRequest("POST", "/ai/chat");
    MockHttpServletResponse res = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(req, res, chain);

    assertThat(chain.getRequest()).isNotNull();
  }

  @Test
  void getRequestWithoutBodyPassesThrough() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/ai/cost/summary");
    MockHttpServletResponse res = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(req, res, chain);

    assertThat(chain.getRequest()).isNotNull();
    assertThat(res.getStatus()).isEqualTo(200);
  }
}
