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
 * W30 #6 审批全链锁定：注册（PENDING，库直插——HTTP 注册路径已由 RegistrationFlowTest 覆盖， 不消耗限流额度）→ admin 会话审批激活 →
 * 用户可登录；审批面门禁（匿名跳登录 / 非 admin 403）。
 */
class UserApprovalFlowTest extends AbstractAuthCenterTest {

  private static final Pattern CSRF = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");

  @Autowired private UserAccountRepository users;

  @Test
  void pendingUserActivatedByAdminCanThenLogin() throws Exception {
    users.insert("approve-me", "{noop}pw", UserAccount.Role.READER, UserAccount.Status.PENDING);

    // admin 会话：登录 → 审批清单可见该用户 → 点激活 → 302 回清单
    HttpClient admin = newBrowser();
    login(admin, "admin", TEST_ADMIN_PASSWORD, true);
    HttpResponse<String> list = get(admin, base() + "/admin/users");
    assertThat(list.statusCode()).isEqualTo(200);
    assertThat(list.body()).contains("approve-me");
    String csrf = extractCsrf(list.body());
    HttpResponse<String> activate =
        postForm(admin, base() + "/admin/users/approve-me/activate", "_csrf=" + csrf);
    assertThat(activate.statusCode()).isEqualTo(302);

    assertThat(users.findByUsername("approve-me").orElseThrow().status())
        .isEqualTo(UserAccount.Status.ACTIVE);

    // 激活后可登录（formLogin 302 且不带 error）
    HttpClient userBrowser = newBrowser();
    login(userBrowser, "approve-me", "pw", true);
  }

  @Test
  void anonymousApprovalAccessRedirectsToLogin() throws Exception {
    HttpResponse<String> response = get(newBrowser(), base() + "/admin/users");

    assertThat(response.statusCode()).isEqualTo(302);
    assertThat(response.headers().firstValue("Location"))
        .hasValueSatisfying(location -> assertThat(location).contains("/login"));
  }

  @Test
  void nonAdminUserCannotAccessApprovalDesk() throws Exception {
    users.insert("reader-joe", "{noop}pw", UserAccount.Role.READER, UserAccount.Status.ACTIVE);
    HttpClient browser = newBrowser();
    login(browser, "reader-joe", "pw", true);

    HttpResponse<String> response = get(browser, base() + "/admin/users");

    assertThat(response.statusCode()).isEqualTo(403);
  }

  /** formLogin 登录；expectSuccess=false 用于断言登录被拒（重定向带 error）。 */
  private void login(HttpClient browser, String username, String password, boolean expectSuccess)
      throws Exception {
    String csrf = extractCsrf(get(browser, base() + "/login").body());
    HttpResponse<String> login =
        postForm(
            browser,
            base() + "/login",
            "username=" + username + "&password=" + password + "&_csrf=" + csrf);
    assertThat(login.statusCode()).isEqualTo(302);
    String location = login.headers().firstValue("Location").orElseThrow();
    if (expectSuccess) {
      assertThat(location).doesNotContain("error");
    } else {
      assertThat(location).contains("error");
    }
  }

  private static String extractCsrf(String html) {
    Matcher matcher = CSRF.matcher(html);
    assertThat(matcher.find()).as("页面应含 _csrf 隐藏域").isTrue();
    return matcher.group(1);
  }
}
