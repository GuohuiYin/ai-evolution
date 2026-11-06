package com.aievolution.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * W12 #3：频率限制行为锁定——固定窗口阈值、429 语义、窗口翻转、身份隔离。
 *
 * <p>时钟注入（{@link AtomicLong} 充当可推进的 epoch 秒），窗口翻转测试不靠 sleep。
 */
class RateLimitFilterTest {

  private static final String KEY_A = "key-alpha";
  private static final String KEY_B = "key-beta";

  private final AtomicLong clock = new AtomicLong(1_800_000_000L); // 固定纪元秒，对齐分钟外
  private final RateLimitFilter filter = new RateLimitFilter(3, clock::get);

  private MockHttpServletRequest requestWithKey(String key) {
    // W30 #2：身份载体收口为 Bearer 头（X-API-Key 已退役）；非 JWT 形态的串按原值整串分桶
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ai/chat");
    request.addHeader("Authorization", "Bearer " + key);
    return request;
  }

  private MockHttpServletResponse pass(String key) throws ServletException, IOException {
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(requestWithKey(key), response, new MockFilterChain());
    return response;
  }

  @Test
  void requestsWithinLimitPassThrough() throws ServletException, IOException {
    for (int i = 0; i < 3; i++) {
      assertThat(pass(KEY_A).getStatus()).isEqualTo(200);
    }
  }

  @Test
  void requestBeyondLimitReturns429WithProblemDetailAndRetryAfter()
      throws ServletException, IOException {
    for (int i = 0; i < 3; i++) {
      pass(KEY_A);
    }

    MockHttpServletResponse response = pass(KEY_A);

    assertThat(response.getStatus()).isEqualTo(429);
    assertThat(response.getContentType()).startsWith("application/problem+json");
    assertThat(response.getContentAsString()).contains("Too Many Requests");
    // Retry-After：距下一窗口的秒数，客户端可自我节流
    assertThat(response.getHeader("Retry-After")).isNotNull();
  }

  @Test
  void limitIsPerIdentity() throws ServletException, IOException {
    for (int i = 0; i < 3; i++) {
      pass(KEY_A);
    }
    assertThat(pass(KEY_A).getStatus()).isEqualTo(429);
    // 另一身份额度独立，不受连坐
    assertThat(pass(KEY_B).getStatus()).isEqualTo(200);
  }

  @Test
  void windowRollsOverAfterSixtySeconds() throws ServletException, IOException {
    for (int i = 0; i < 3; i++) {
      pass(KEY_A);
    }
    assertThat(pass(KEY_A).getStatus()).isEqualTo(429);

    clock.addAndGet(61);

    assertThat(pass(KEY_A).getStatus()).isEqualTo(200);
  }

  @Test
  void jwtTokensWithSameSubjectShareBucket() throws ServletException, IOException {
    // W30 #2：JWT 按 sub 分桶——同一主体换签名重发（如刷新 token 后）仍吃同一额度
    String tokenAlice1 = fakeJwt("alice", "sig-1");
    String tokenAlice2 = fakeJwt("alice", "sig-2");
    for (int i = 0; i < 2; i++) {
      assertThat(pass(tokenAlice1).getStatus()).isEqualTo(200);
    }
    assertThat(pass(tokenAlice2).getStatus()).isEqualTo(200);
    // 第 4 次：同 sub 已超限，无论持哪一串 token
    assertThat(pass(tokenAlice2).getStatus()).isEqualTo(429);
    // 另一 sub 额度独立
    assertThat(pass(fakeJwt("bob", "sig-3")).getStatus()).isEqualTo(200);
  }

  /** 伪造三段式 JWT（仅测试分桶取值，不验签——限流不是安全决策） */
  private static String fakeJwt(String sub, String signature) {
    String payload =
        java.util.Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                ("{\"sub\":\"" + sub + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    return "header." + payload + "." + signature;
  }

  @Test
  void anonymousRequestsShareBucketByIp() throws ServletException, IOException {
    // 无凭证试探按 IP 记账（限流排在鉴权前：401 不是免费通道）
    for (int i = 0; i < 3; i++) {
      MockHttpServletResponse response = new MockHttpServletResponse();
      filter.doFilter(
          new MockHttpServletRequest("POST", "/ai/chat"), response, new MockFilterChain());
      assertThat(response.getStatus()).isEqualTo(200);
    }
    MockHttpServletResponse fourth = new MockHttpServletResponse();
    filter.doFilter(new MockHttpServletRequest("POST", "/ai/chat"), fourth, new MockFilterChain());
    assertThat(fourth.getStatus()).isEqualTo(429);
  }

  @Test
  void publicPathsAreNotRateLimited() throws ServletException, IOException {
    // 健康探针不限流：kubelet 高频探测不该吃业务额度
    for (int i = 0; i < 10; i++) {
      MockHttpServletResponse response = new MockHttpServletResponse();
      filter.doFilter(
          new MockHttpServletRequest("GET", "/actuator/health"), response, new MockFilterChain());
      assertThat(response.getStatus()).isEqualTo(200);
    }
  }

  @Test
  void zeroLimitFailsFast() {
    assertThatThrownBy(() -> new RateLimitFilter(0, clock::get))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("per-minute");
  }
}
