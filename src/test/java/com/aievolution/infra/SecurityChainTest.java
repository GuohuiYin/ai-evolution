package com.aievolution.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * W29 #1 链级行为锁定（admin key 上下文）：401 语义、Bearer 正史、X-API-Key 兼容期、公开面放行。
 *
 * <p>403 语义由 reader 上下文锁定，见 {@link SecurityChainRbacTest}。
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.ai.deepseek.api-key=test-key",
      "spring.ai.openai.api-key=test-key",
      "spring.ai.vectorstore.qdrant.initialize-schema=false",
      "ai.knowledge.ingest.enabled=false",
      "spring.ai.mcp.client.enabled=false",
      "ai.security.api-key=test-key"
    })
class SecurityChainTest {

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
  }

  @Test
  void wrongBearerTokenReturns401() throws Exception {
    HttpResponse<String> response = send("POST", "/ai/chat", "Bearer wrong-key");

    assertThat(response.statusCode()).isEqualTo(401);
  }

  @Test
  void validBearerTokenPassesAuthentication() throws Exception {
    HttpResponse<String> response = send("GET", "/ai/cost/summary", "Bearer test-key");

    assertThat(response.statusCode()).isEqualTo(200);
  }

  @Test
  void legacyApiKeyHeaderStillAcceptedDuringCompatWindow() throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/ai/cost/summary"))
            .header("X-API-Key", "test-key")
            .GET()
            .build();

    HttpResponse<String> response =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
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
}
