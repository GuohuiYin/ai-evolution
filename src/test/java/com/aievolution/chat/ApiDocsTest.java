package com.aievolution.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/** OpenAPI 契约测试（DoD 第⑤条）：文档端点可访问，且声明的接口与错误码与实现一致。 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.ai.deepseek.api-key=test-key",
      "spring.ai.openai.api-key=test-key",
      "spring.ai.vectorstore.qdrant.initialize-schema=false",
      "ai.knowledge.ingest.enabled=false",
      // W12 #1：测试上下文不起 uvx 外部子进程（CI 无 uvx），MCP Client 链路由专门测试覆盖
      "spring.ai.mcp.client.enabled=false",
      // W12 #2：鉴权为强制项（空 key 启动 fail-fast），测试上下文配测试 key
      "ai.security.api-key=test-key"
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
  void apiDocsDeclaresApiKeySecurityScheme() throws Exception {
    // W12 #2b（A7）：契约必须声明安全方案——路径可匿名读文档，调接口必须持证
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).build();

    HttpResponse<String> response =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.body())
        .contains("\"securitySchemes\"")
        .contains("apiKey")
        .contains("X-API-Key")
        .contains("\"security\""); // 全局安全要求
  }
}
