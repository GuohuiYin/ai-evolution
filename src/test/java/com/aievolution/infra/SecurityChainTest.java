package com.aievolution.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * W30 #2 链级行为锁定（admin JWT 上下文）：401 语义、JWT 验签正史、旧凭证双通道退役、公开面放行。
 *
 * <p>验签走真 JWKS（{@link TestJwtSupport} 进程内 RSA + 内嵌端点），不打桩。 403 语义由 reader 上下文锁定，见 {@link
 * SecurityChainRbacTest}。
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.ai.deepseek.api-key=test-key",
      "spring.ai.openai.api-key=test-key",
      "spring.ai.vectorstore.qdrant.initialize-schema=false",
      "ai.knowledge.ingest.enabled=false",
      "spring.ai.mcp.client.enabled=false",
      // W29 #3：测试上下文显式声明一个白名单 origin（生产默认空 = deny-by-default）
      "ai.security.cors.allowed-origins=http://localhost:5173"
    })
class SecurityChainTest {

  @DynamicPropertySource
  static void jwtProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri", TestJwtSupport::jwkSetUri);
  }

  @Value("${local.server.port}")
  private int port;

  private HttpResponse<String> send(String method, String path, String authorizationHeader)
      throws Exception {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    if (authorizationHeader != null) {
      builder.header("Authorization", authorizationHeader);
    }
    HttpRequest request =
        switch (method) {
          case "GET" -> builder.GET().build();
          default -> builder.POST(HttpRequest.BodyPublishers.noBody()).build();
        };
    return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void missingCredentialReturns401ProblemDetailWithBearerChallenge() throws Exception {
    HttpResponse<String> response = send("POST", "/ai/chat", null);

    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(response.headers().firstValue("Content-Type"))
        .hasValueSatisfying(type -> assertThat(type).startsWith("application/problem+json"));
    assertThat(response.headers().firstValue("WWW-Authenticate")).hasValue("Bearer");
    // 无状态姿态：401 不得顺手发 JSESSIONID（否则 CSRF 关闭的论证自相矛盾）
    assertThat(response.headers().allValues("Set-Cookie")).noneMatch(v -> v.contains("JSESSIONID"));
  }

  @Test
  void wrongBearerTokenReturns401() throws Exception {
    HttpResponse<String> response = send("POST", "/ai/chat", "Bearer wrong-key");

    assertThat(response.statusCode()).isEqualTo(401);
  }

  @Test
  void validJwtWithAdminRolePassesAuthentication() throws Exception {
    String token = TestJwtSupport.issueToken("admin-user", "admin", null);
    HttpResponse<String> response = send("GET", "/ai/cost/summary", "Bearer " + token);

    assertThat(response.statusCode()).isEqualTo(200);
  }

  @Test
  void machineScopeJwtPassesViaScopeClaim() throws Exception {
    // 机器通道（client_credentials）：角色来自 scope 声明；writer ⊇ reader（层级向下兼容）
    String token = TestJwtSupport.issueToken("mcp-client", null, "writer");
    HttpResponse<String> response = send("GET", "/ai/cost/summary", "Bearer " + token);

    assertThat(response.statusCode()).isEqualTo(200);
  }

  @Test
  void legacyApiKeyHeaderNoLongerAccepted() throws Exception {
    // W30 #2 直切（Owner 裁决）：X-API-Key 与 opaque Bearer 同批退役，不再有任何兼容通道
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/ai/cost/summary"))
            .header("X-API-Key", "test-key")
            .GET()
            .build();

    HttpResponse<String> response =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(401);
  }

  @Test
  void publicHealthProbeRequiresNoCredential() throws Exception {
    HttpResponse<String> response = send("GET", "/actuator/health", null);

    assertThat(response.statusCode()).isEqualTo(200);
  }

  @Test
  void securityHeadersSuitePresentOnResponses() throws Exception {
    // W29 #2：头套件断言——公开面与持证端点同样带头（健康探针零成本取样）
    HttpResponse<String> response = send("GET", "/actuator/health", null);

    var headers = response.headers();
    assertThat(headers.firstValue("X-Content-Type-Options")).hasValue("nosniff");
    assertThat(headers.firstValue("X-Frame-Options")).hasValue("DENY");
    assertThat(headers.firstValue("Referrer-Policy")).hasValue("no-referrer");
    assertThat(headers.firstValue("Content-Security-Policy"))
        .hasValueSatisfying(csp -> assertThat(csp).contains("default-src 'self'"));
  }

  @Test
  void corsPreflightFromWhitelistedOriginIsPermitted() throws Exception {
    // W29 #3：白名单 origin 的预检放行（CorsFilter 在鉴权前处理，不吃 401）
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/ai/chat"))
            .header("Origin", "http://localhost:5173")
            .header("Access-Control-Request-Method", "POST")
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
            .build();

    HttpResponse<String> response =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Access-Control-Allow-Origin"))
        .hasValue("http://localhost:5173");
  }

  @Test
  void corsPreflightFromUnknownOriginIsDenied() throws Exception {
    // deny-by-default：未登记 origin 的预检不得拿到 Allow-Origin 头
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/ai/chat"))
            .header("Origin", "http://evil.example")
            .header("Access-Control-Request-Method", "POST")
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
            .build();

    HttpResponse<String> response =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
  }
}
