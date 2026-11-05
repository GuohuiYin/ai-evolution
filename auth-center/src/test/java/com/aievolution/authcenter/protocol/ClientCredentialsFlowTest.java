package com.aievolution.authcenter.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import com.aievolution.authcenter.AbstractAuthCenterTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
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
        requestToken(
            basic("mcp-client", "test-secret"), "grant_type=client_credentials&scope=writer");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"access_token\"");
    assertThat(response.body()).contains("\"token_type\":\"Bearer\"");
    // JWT 载荷解码（验签是资源服务器侧 #2 的事）：iss=签发方标识、scope 含 writer
    String token = response.body().split("\"access_token\":\"")[1].split("\"")[0];
    String payload =
        new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
    assertThat(payload).contains("\"iss\":\"http://localhost:19000\"");
    assertThat(payload).contains("writer");
  }

  @Test
  void rejectsWrongSecret() throws Exception {
    HttpResponse<String> response =
        requestToken(basic("mcp-client", "wrong"), "grant_type=client_credentials");

    assertThat(response.statusCode()).isEqualTo(401);
  }

  @Test
  void rejectsUnknownClient() throws Exception {
    HttpResponse<String> response =
        requestToken(basic("ghost", "test-secret"), "grant_type=client_credentials");

    assertThat(response.statusCode()).isEqualTo(401);
  }

  @Test
  void jwksEndpointExposesRsaPublicKey() throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/oauth2/jwks"))
            .GET()
            .build();

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"kty\":\"RSA\"");
  }

  private HttpResponse<String> requestToken(String basicCredentials, String formBody)
      throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/oauth2/token"))
            .header("Authorization", "Basic " + basicCredentials)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(formBody))
            .build();
    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private static String basic(String id, String secret) {
    return Base64.getEncoder().encodeToString((id + ":" + secret).getBytes(StandardCharsets.UTF_8));
  }
}
