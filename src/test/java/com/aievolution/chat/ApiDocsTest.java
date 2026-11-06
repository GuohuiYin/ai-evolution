package com.aievolution.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * OpenAPI 契约测试（DoD 第⑤条）：文档端点可访问，且声明的接口与错误码与实现一致。
 *
 * <p>W30 #2：契约安全方案 = Bearer JWT（bearerFormat: JWT）；旧 X-API-Key 方案声明已随直切移除。
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.ai.deepseek.api-key=test-key",
      "spring.ai.openai.api-key=test-key",
      "spring.ai.vectorstore.qdrant.initialize-schema=false",
      "ai.knowledge.ingest.enabled=false",
      // W12 #1：测试上下文不起 uvx 外部子进程（CI 无 uvx），MCP Client 链路由专门测试覆盖
      "spring.ai.mcp.client.enabled=false"
    })
class ApiDocsTest {

  @Value("${local.server.port}")
  private int port;

  @Test
  void apiDocsExposesChatEndpointWithDocumentedErrorCodes() throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).build();

    HttpResponse<String> response =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body())
        .contains("/ai/chat") // 接口路径已登记
        .contains("\"200\"", "\"400\"", "\"502\"", "\"503\""); // 全部声明过的响应码
  }

  @Test
  void apiDocsDeclaresBearerJwtSecurityScheme() throws Exception {
    // W30 #2（A7）：契约必须声明安全方案——路径可匿名读文档，调接口必须持 JWT
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).build();

    HttpResponse<String> response =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.body())
        .contains("\"securitySchemes\"")
        .contains("bearer") // Bearer JWT 正史
        .contains("\"bearerFormat\":\"JWT\"")
        .doesNotContain("X-API-Key") // 直切：兼容期方案声明已移除
        .contains("\"security\"") // 全局安全要求
        .contains("\"429\""); // W12 #3：限流响应码全端点声明
  }
}
