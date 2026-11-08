package com.aievolution.authcenter.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import com.aievolution.authcenter.AbstractAuthCenterTest;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** W30 #1 步骤 2 机器通道行为锁定：客户端凭证流签发 JWT / 错密钥与未知客户端 401 / JWKS 公钥端点可匿名拉取（资源服务器 #2 验签依赖它）。 */
class ClientCredentialsFlowTest extends AbstractAuthCenterTest {

  private final HttpClient client = HttpClient.newHttpClient();

  @Test
  void issuesJwtForValidClientCredentials() throws Exception {
    HttpResponse<String> response =
        postForm(
            client,
            base() + "/oauth2/token",
            "grant_type=client_credentials&scope=writer",
            "Authorization",
            "Basic " + basic("mcp-client", "test-secret"));

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"access_token\"");
    assertThat(response.body()).contains("\"token_type\":\"Bearer\"");
    // JWT 载荷解码（验签是资源服务器侧 #2 的事）：iss=签发方标识、scope 含 writer、无 role（机器通道）
    String token = response.body().split("\"access_token\":\"")[1].split("\"")[0];
    String payload =
        new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
    assertThat(payload).contains("\"iss\":\"http://localhost:19000\"");
    assertThat(payload).contains("writer");
    assertThat(payload).doesNotContain("\"role\"");
  }

  @Test
  void tokenWithoutScopeRequestCarriesNoScopeClaim() throws Exception {
    // SAS 行为锁定（2026-11-08 实踩）：不显式请求 scope 时令牌不含 scope 声明——
    // 调用方（scripts/auth_token.py 等）必须显式带 scope=writer，否则资源服务器侧零角色 403
    HttpResponse<String> response =
        postForm(
            client,
            base() + "/oauth2/token",
            "grant_type=client_credentials",
            "Authorization",
            "Basic " + basic("mcp-client", "test-secret"));

    assertThat(response.statusCode()).isEqualTo(200);
    String token = response.body().split("\"access_token\":\"")[1].split("\"")[0];
    String payload =
        new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
    assertThat(payload).doesNotContain("\"scope\"");
  }

  @Test
  void rejectsWrongSecret() throws Exception {
    HttpResponse<String> response =
        postForm(
            client,
            base() + "/oauth2/token",
            "grant_type=client_credentials",
            "Authorization",
            "Basic " + basic("mcp-client", "wrong"));

    assertThat(response.statusCode()).isEqualTo(401);
  }

  @Test
  void rejectsUnknownClient() throws Exception {
    HttpResponse<String> response =
        postForm(
            client,
            base() + "/oauth2/token",
            "grant_type=client_credentials",
            "Authorization",
            "Basic " + basic("ghost", "test-secret"));

    assertThat(response.statusCode()).isEqualTo(401);
  }

  @Test
  void jwksEndpointExposesRsaPublicKey() throws Exception {
    HttpResponse<String> response = get(client, base() + "/oauth2/jwks");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"kty\":\"RSA\"");
  }

  private static String basic(String id, String secret) {
    return Base64.getEncoder().encodeToString((id + ":" + secret).getBytes(StandardCharsets.UTF_8));
  }
}
