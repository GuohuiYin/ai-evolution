package com.aievolution.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * W12 #2：API Key 过滤器行为锁定——默认拒绝 + 显式放行清单。
 *
 * <p>放行清单的每一项都是一条显式决策（对话页静态资源 / 本地文档 / K8s 健康探针）， 新增放行路径必须先改这里的参数化清单——让"开口子"成为一次可 review 的决策。
 */
class ApiKeyAuthFilterTest {

  private static final String KEY = "test-key";

  private final ApiKeyAuthFilter filter = new ApiKeyAuthFilter(KEY);

  @Test
  void protectedPathWithoutKeyReturns401ProblemJson() throws ServletException, IOException {
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(new MockHttpServletRequest("POST", "/ai/chat"), response, chain);

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentType()).startsWith("application/problem+json");
    assertThat(response.getContentAsString()).contains("Unauthorized").contains("X-API-Key");
    // 请求不得穿透到下游
    assertThat(chain.getRequest()).isNull();
  }

  @Test
  void protectedPathWithWrongKeyReturns401() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
    request.addHeader(ApiKeyAuthFilter.API_KEY_HEADER, "wrong-key");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(chain.getRequest()).isNull();
  }

  @Test
  void protectedPathWithValidKeyPassesThrough() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ai/chat/stream");
    request.addHeader(ApiKeyAuthFilter.API_KEY_HEADER, KEY);
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(chain.getRequest()).isNotNull();
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/",
        "/index.html",
        "/favicon.ico",
        "/swagger-ui/index.html",
        "/v3/api-docs",
        "/v3/api-docs.yaml",
        "/actuator/health",
        "/actuator/health/liveness"
      })
  void allowlistedPathsPassWithoutKey(String path) throws ServletException, IOException {
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(new MockHttpServletRequest("GET", path), response, chain);

    assertThat(chain.getRequest()).as("放行路径 %s 不应拦截", path).isNotNull();
  }

  @Test
  void actuatorMetricsRequiresKey() throws ServletException, IOException {
    // 只放行健康探针（K8s kubelet 无凭证）；metrics/info 含内部信息，必须持证
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(new MockHttpServletRequest("GET", "/actuator/metrics"), response, chain);

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(chain.getRequest()).isNull();
  }

  @Test
  void blankConfiguredKeyFailsFast() {
    // 鉴权没有"忘记开"的静默中间态：配置缺失 = 启动失败
    assertThatThrownBy(() -> new ApiKeyAuthFilter(" "))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("AI_EVOLUTION_API_KEY");
  }
}
