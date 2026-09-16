package com.aievolution.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * W6-Step2 集成测试：MCP Server（Streamable HTTP）真实起服务、真实发 JSON-RPC， 验证 initialize 握手与 tools/list
 * 暴露现有三个工具。不触网、不调真实模型。
 *
 * <p>注意：Streamable HTTP 要求 Accept 同时声明 application/json 与 text/event-stream（规范硬性要求）。
 */
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
class McpServerIT {

  @Value("${local.server.port}")
  private int port;

  private final HttpClient http = HttpClient.newHttpClient();

  private HttpRequest.Builder mcpPost(String body) {
    // Streamable HTTP 规范：客户端必须同时接受 JSON 与 SSE 流两种响应形态
    // W12 #2：/mcp 属保护端点，请求必须持证（X-API-Key）
    return HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/mcp"))
        .header("Content-Type", "application/json")
        .header("Accept", "application/json, text/event-stream")
        .header("X-API-Key", "test-key")
        .POST(HttpRequest.BodyPublishers.ofString(body));
  }

  @Test
  void mcpWithoutApiKeyReturns401() throws Exception {
    // W12 #2 集成锁定：MCP 端点无凭证 = 401 ProblemDetail（真实 HTTP 层验证，非单测模拟）
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/mcp"))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}"))
            .build();

    HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(resp.statusCode()).isEqualTo(401);
    assertThat(resp.headers().firstValue("Content-Type"))
        .hasValueSatisfying(ct -> assertThat(ct).startsWith("application/problem+json"));
    assertThat(resp.body()).contains("Unauthorized");
  }

  @Test
  void initializeHandshakeReturnsServerInfo() throws Exception {
    String initialize =
        """
        {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"mcp-it","version":"1.0"}}}
        """;

    HttpResponse<String> resp =
        http.send(mcpPost(initialize).build(), HttpResponse.BodyHandlers.ofString());

    assertThat(resp.statusCode()).isEqualTo(200);
    assertThat(resp.body()).contains("protocolVersion").contains("ai-evolution");
  }

  @Test
  void errorResponseNeverLeaksStackTrace() throws Exception {
    // 红队基线 M1 实锤漏洞（P0）：无会话的业务请求曾泄漏完整 Java 堆栈（类名/行号）。
    // 安全契约：任何错误响应只允许 JSON-RPC 规范字段，禁止 stackTrace/className 等内部信息
    String toolsListNoSession =
        """
        {"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}
        """;

    HttpResponse<String> resp =
        http.send(mcpPost(toolsListNoSession).build(), HttpResponse.BodyHandlers.ofString());

    assertThat(resp.body()).doesNotContain("stackTrace").doesNotContain("className");
  }

  @Test
  void toolsListExposesExistingThreeTools() throws Exception {
    String initialize =
        """
        {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"mcp-it","version":"1.0"}}}
        """;
    HttpResponse<String> initResp =
        http.send(mcpPost(initialize).build(), HttpResponse.BodyHandlers.ofString());
    assertThat(initResp.statusCode()).isEqualTo(200);

    HttpRequest.Builder followUp = mcpPost("");
    initResp
        .headers()
        .firstValue("Mcp-Session-Id")
        .ifPresent(id -> followUp.header("Mcp-Session-Id", id));

    // 生命周期要求：initialized 通知之后才允许业务请求
    String initialized =
        """
        {"jsonrpc":"2.0","method":"notifications/initialized"}
        """;
    http.send(
        followUp.copy().POST(HttpRequest.BodyPublishers.ofString(initialized)).build(),
        HttpResponse.BodyHandlers.ofString());

    String toolsList =
        """
        {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
        """;
    HttpResponse<String> resp =
        http.send(
            followUp.copy().POST(HttpRequest.BodyPublishers.ofString(toolsList)).build(),
            HttpResponse.BodyHandlers.ofString());

    assertThat(resp.statusCode()).isEqualTo(200);
    assertThat(resp.body())
        .contains("getDailyQuotes")
        .contains("getFinancialSummary")
        .contains("searchAnnouncements");
  }
}
