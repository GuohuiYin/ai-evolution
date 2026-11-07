package com.aievolution.authcenter.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.aievolution.authcenter.AbstractAuthCenterTest;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * W30 #6 注册行为锁定：注册即 PENDING + READER + argon2 落库（明文不过夜）； 用户名占用与成功同文案（防枚举）；pending 登录拒绝由
 * AuthorizationCodeFlowTest 预锁。 限流 429 由 {@link RegistrationRateLimitTest} 独立上下文锁定（额度共享互不误伤）。
 */
class RegistrationFlowTest extends AbstractAuthCenterTest {

  private static final Pattern CSRF = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");
  private static final String GENERIC_REPLY = "注册请求已提交，待管理员审批通过后生效";

  @Autowired private UserAccountRepository users;

  @Test
  void registrationCreatesPendingReaderWithArgon2Hash() throws Exception {
    HttpResponse<String> response = register("new-user-1", "password-123");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains(GENERIC_REPLY);

    UserAccount account = users.findByUsername("new-user-1").orElseThrow();
    assertThat(account.status()).isEqualTo(UserAccount.Status.PENDING);
    assertThat(account.role()).isEqualTo(UserAccount.Role.READER);
    // 明文不过夜：落库的是 argon2 哈希而非口令本身
    assertThat(account.passwordHash()).startsWith("{argon2}").doesNotContain("password-123");
  }

  @Test
  void duplicateUsernameGetsSameGenericReply() throws Exception {
    register("dup-user", "password-123");

    HttpResponse<String> second = register("dup-user", "password-456");

    // 防用户名枚举：占用与成功同文案同状态码
    assertThat(second.statusCode()).isEqualTo(200);
    assertThat(second.body()).contains(GENERIC_REPLY);
  }

  @Test
  void invalidInputIsRejectedWithFormError() throws Exception {
    HttpResponse<String> response = register("x", "password-123"); // 用户名过短

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.body()).contains("3-32 位");
  }

  /** 走浏览器链路注册（GET 表单取 CSRF → POST 提交）。 */
  private HttpResponse<String> register(String username, String password) throws Exception {
    HttpClient browser = newBrowser();
    String csrf = extractCsrf(get(browser, base() + "/register").body());
    return postForm(
        browser,
        base() + "/register",
        "username=" + username + "&password=" + password + "&_csrf=" + csrf);
  }

  private static String extractCsrf(String html) {
    Matcher matcher = CSRF.matcher(html);
    assertThat(matcher.find()).as("注册表单应含 _csrf 隐藏域").isTrue();
    return matcher.group(1);
  }
}
