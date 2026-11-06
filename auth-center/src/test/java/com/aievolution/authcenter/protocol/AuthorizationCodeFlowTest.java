package com.aievolution.authcenter.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import com.aievolution.authcenter.AbstractAuthCenterTest;
import com.aievolution.authcenter.identity.UserAccount;
import com.aievolution.authcenter.identity.UserAccountRepository;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * W30 #1 步骤 3b 人通道行为锁定：授权码+PKCE 全链（未登录跳登录页 → 登录 → 回跳带 code → 换得 JWT 含 role 声明）；错 verifier
 * 拒发；pending 用户登录被拒（#6 语义预锁）。
 */
class AuthorizationCodeFlowTest extends AbstractAuthCenterTest {

  private static final String REDIRECT_URI = "http://localhost:18080/callback";
  private static final Pattern CSRF = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");

  @Autowired private UserAccountRepository users;

  @Test
  void authorizationCodePkceFlowIssuesJwtWithRoleClaim() throws Exception {
    String verifier = newVerifier();
    String code = loginAndCaptureCode("admin", TEST_ADMIN_PASSWORD, verifier);
    assertThat(code).isNotNull();

    HttpResponse<String> token = exchangeToken(code, verifier);

    assertThat(token.statusCode()).isEqualTo(200);
    String jwt = token.body().split("\"access_token\":\"")[1].split("\"")[0];
    String payload =
        new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), StandardCharsets.UTF_8);
    assertThat(payload).contains("\"iss\":\"http://localhost:19000\"");
    assertThat(payload).contains("\"role\":\"admin\"");
    assertThat(payload).contains("writer");
  }

  @Test
  void wrongVerifierIsRejected() throws Exception {
    String code = loginAndCaptureCode("admin", TEST_ADMIN_PASSWORD, newVerifier());
    assertThat(code).isNotNull();

    HttpResponse<String> token = exchangeToken(code, newVerifier());

    assertThat(token.statusCode()).isEqualTo(400);
    assertThat(token.body()).contains("invalid_grant");
  }

  @Test
  void pendingUserLoginIsRejected() throws Exception {
    users.insert("pending-login", "{noop}pw", UserAccount.Role.READER, UserAccount.Status.PENDING);

    String code = loginAndCaptureCode("pending-login", "pw", newVerifier());

    assertThat(code).isNull();
  }

  /** 走完「authorize → 登录 → 回跳」浏览器链路，返回授权码；登录被拒返回 null。 */
  private String loginAndCaptureCode(String username, String password, String verifier)
      throws Exception {
    HttpClient browser = newBrowser();
    String authorizeUrl =
        base()
            + "/oauth2/authorize?response_type=code&client_id=ai-evolution-web&redirect_uri="
            + URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8)
            + "&scope=writer&code_challenge="
            + pkceChallenge(verifier)
            + "&code_challenge_method=S256";

    // 未登录 → 302 登录页
    HttpResponse<String> knock = get(browser, authorizeUrl);
    assertThat(knock.statusCode()).isEqualTo(302);
    assertThat(knock.headers().firstValue("Location"))
        .hasValueSatisfying(location -> assertThat(location).contains("/login"));

    // 登录页取 CSRF token（formLogin 默认开启 CSRF 防护）
    String csrf = extractCsrf(get(browser, base() + "/login").body());

    // 提交登录表单
    HttpResponse<String> login =
        postForm(
            browser,
            base() + "/login",
            "username=" + username + "&password=" + password + "&_csrf=" + csrf);
    assertThat(login.statusCode()).isEqualTo(302);
    String location = login.headers().firstValue("Location").orElseThrow();
    if (location.contains("error")) {
      return null; // 认证失败（含 pending 禁用）→ 回登录页
    }

    // 跟随回跳链直到 redirect_uri?code=...
    while (!location.startsWith(REDIRECT_URI)) {
      HttpResponse<String> hop = get(browser, absolute(location));
      assertThat(hop.statusCode()).isEqualTo(302);
      location = hop.headers().firstValue("Location").orElseThrow();
    }
    return location.split("code=")[1].split("&")[0];
  }

  /** 授权码换令牌（public client：client_id 在表单体、无密钥，靠 PKCE verifier 验明正身）。 */
  private HttpResponse<String> exchangeToken(String code, String verifier) throws Exception {
    return postForm(
        HttpClient.newHttpClient(),
        base() + "/oauth2/token",
        "grant_type=authorization_code&client_id=ai-evolution-web&redirect_uri="
            + URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8)
            + "&code="
            + code
            + "&code_verifier="
            + verifier);
  }

  private String absolute(String location) {
    return location.startsWith("http") ? location : base() + location;
  }

  private static String newVerifier() {
    return "verifier-" + UUID.randomUUID(); // PKCE 规范要求 43-128 字符
  }

  private static String pkceChallenge(String verifier) throws Exception {
    byte[] digest =
        MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.UTF_8));
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
  }

  private static String extractCsrf(String html) {
    Matcher matcher = CSRF.matcher(html);
    assertThat(matcher.find()).as("登录页应含 _csrf 隐藏域").isTrue();
    return matcher.group(1);
  }
}
