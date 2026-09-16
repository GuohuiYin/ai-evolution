package com.aievolution.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/** 对话页面冒烟测试：首页可访问且返回 HTML（详细交互逻辑由人工浏览器验收兜底）。 */
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
class ChatPageTest {

  @Value("${local.server.port}")
  private int port;

  @Test
  void homePageServesChatUi() throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/")).build();

    HttpResponse<String> response =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Content-Type"))
        .hasValueSatisfying(v -> assertThat(v).contains("text/html"));
    assertThat(response.body()).contains("AI Evolution");
    // W11 #2：SSE trajectory 事件的页面挂钩必须在位（可选展示研究轨迹）
    assertThat(response.body()).contains("onTrajectory").contains("研究轨迹");
  }
}
