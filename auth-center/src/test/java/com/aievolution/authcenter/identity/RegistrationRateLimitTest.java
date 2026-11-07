package com.aievolution.authcenter.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.aievolution.authcenter.AbstractAuthCenterTest;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * W30 #6 注册限流锁定：每 IP 每小时 N 次（本上下文配 3），超限 429。 独立上下文（属性不同 → 上下文缓存键不同）——与 {@link
 * RegistrationFlowTest} 的额度互不误伤。
 */
class RegistrationRateLimitTest extends AbstractAuthCenterTest {

  private static final Pattern CSRF = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");

  @DynamicPropertySource
  static void rateLimitProps(DynamicPropertyRegistry registry) {
    registry.add("auth.registration.rate-limit-per-hour", () -> "3");
  }

  @Test
  void registrationBeyondQuotaReturns429() throws Exception {
    for (int i = 1; i <= 3; i++) {
      assertThat(register("rl-user-" + i, "password-123").statusCode()).isEqualTo(200);
    }

    HttpResponse<String> fourth = register("rl-user-4", "password-123");

    assertThat(fourth.statusCode()).isEqualTo(429);
  }

  private HttpResponse<String> register(String username, String password) throws Exception {
    HttpClient browser = newBrowser();
    Matcher matcher = CSRF.matcher(get(browser, base() + "/register").body());
    assertThat(matcher.find()).isTrue();
    return postForm(
        browser,
        base() + "/register",
        "username=" + username + "&password=" + password + "&_csrf=" + matcher.group(1));
  }
}
