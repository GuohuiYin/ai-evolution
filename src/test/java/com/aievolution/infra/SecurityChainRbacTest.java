package com.aievolution.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/** W29 #1 链级行为锁定（reader key 上下文）：403 语义与端点角色映射—— 凭证合法但角色不足的端点返回 403 ProblemDetail，角色足够的端点正常放行。 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.ai.deepseek.api-key=test-key",
      "spring.ai.openai.api-key=test-key",
      "spring.ai.vectorstore.qdrant.initialize-schema=false",
      "ai.knowledge.ingest.enabled=false",
      "spring.ai.mcp.client.enabled=false",
      "ai.security.api-key=test-key",
      // 本上下文 key 只授 reader：验证 403（授权不足）与 401（认证失败）已分清
      "ai.security.key-roles=reader"
    })
class SecurityChainRbacTest {

  @Value("${local.server.port}")
  private int port;

  private HttpResponse<String> send(String method, String path) throws Exception {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Authorization", "Bearer test-key");
    HttpRequest request =
        switch (method) {
          case "GET" -> builder.GET().build();
          default -> builder.POST(HttpRequest.BodyPublishers.noBody()).build();
        };
    return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void readerRoleCanReadCostSummary() throws Exception {
    HttpResponse<String> response = send("GET", "/ai/cost/summary");

    assertThat(response.statusCode()).isEqualTo(200);
  }

  @Test
  void readerRoleCannotAdjustBudgetReturns403ProblemDetail() throws Exception {
    // POST /ai/cost/budget 映射 admin——reader 持证但角色不足：403（不是 401）
    HttpResponse<String> response = send("POST", "/ai/cost/budget");

    assertThat(response.statusCode()).isEqualTo(403);
    assertThat(response.headers().firstValue("Content-Type"))
        .hasValueSatisfying(type -> assertThat(type).startsWith("application/problem+json"));
  }

  @Test
  void readerRoleCannotChatReturns403() throws Exception {
    // POST /ai/chat 映射 writer——层级向下兼容不向上：reader 不蕴含 writer
    HttpResponse<String> response = send("POST", "/ai/chat");

    assertThat(response.statusCode()).isEqualTo(403);
  }
}
