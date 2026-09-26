package com.aievolution.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpClientCommonProperties;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStdioClientProperties;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStreamableHttpClientProperties;
import tools.jackson.databind.json.JsonMapper;

/**
 * W14 #1：连接工厂的传输选择三分支（纯逻辑，不拉起子进程、不开网络）。 锁定契约：stdio 优先（本地形态）→ streamable-http（k8s 形态）→ 都没有即配置错误
 * fail-fast。
 */
class PropertiesMcpFetchConnectionFactoryTest {

  private final McpStdioClientProperties stdioProperties = new McpStdioClientProperties();
  private final McpStreamableHttpClientProperties httpProperties =
      new McpStreamableHttpClientProperties();
  private final McpClientCommonProperties commonProperties = new McpClientCommonProperties();

  private PropertiesMcpFetchConnectionFactory factory() {
    commonProperties.setName("test-client");
    commonProperties.setRequestTimeout(Duration.ofSeconds(30));
    return new PropertiesMcpFetchConnectionFactory(
        stdioProperties,
        httpProperties,
        commonProperties,
        JsonMapper.builder().build(),
        "0.0-test");
  }

  @Test
  void stdioConnectionSelectedWhenConfigured() {
    stdioProperties
        .getConnections()
        .put(
            "fetch",
            new McpStdioClientProperties.Parameters("uvx", List.of("mcp-server-fetch"), Map.of()));

    assertThat(factory().newTransport()).isInstanceOf(StdioClientTransport.class);
  }

  @Test
  void streamableHttpSelectedWhenStdioAbsent() {
    httpProperties
        .getConnections()
        .put(
            "fetch",
            new McpStreamableHttpClientProperties.ConnectionParameters(
                "http://mcp-fetch:8000", "/mcp"));

    assertThat(factory().newTransport()).isInstanceOf(HttpClientStreamableHttpTransport.class);
  }

  @Test
  void missingFetchConnectionFailsFast() {
    assertThatThrownBy(() -> factory().newTransport())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("fetch");
  }
}
