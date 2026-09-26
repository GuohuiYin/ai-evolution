package com.aievolution.mcp;

import com.aievolution.tool.ExternalToolClient;
import io.modelcontextprotocol.client.McpSyncClient;
import java.util.List;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpClientCommonProperties;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStdioClientProperties;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStreamableHttpClientProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * MCP Client 工具接线（W12 #1b）：从自动装配的 sync client 列表中挑出 fetch 连接， 封装为 tool 域 {@link ExternalToolClient}
 * 契约 Bean——协议选型与连接识别不出 mcp 域。
 *
 * <p>仅在 MCP Client 启用时注册（测试上下文关闭，不起 uvx 子进程）；启用而连接缺失 属于配置错误，启动 fail-fast 而非运行期才发现。
 *
 * <p>W14 #1：fetch 连接再包一层 {@link McpConnectionManager}——长驻 stdio 会话静默死亡时
 * 按需重建（工厂复用同一份绑定属性，重建连接与启动期连接同参），替代"重启应用恢复"。
 */
@Configuration
@ConditionalOnProperty(name = "spring.ai.mcp.client.enabled", havingValue = "true")
// 幂等声明：客户端自动装配已启用这三组属性时重复注册为 no-op；显式声明保证重建工厂注入不依赖装配顺序
@EnableConfigurationProperties({
  McpStdioClientProperties.class,
  McpStreamableHttpClientProperties.class,
  McpClientCommonProperties.class
})
public class McpClientToolConfiguration {

  @Bean
  public ExternalToolClient externalToolClient(
      List<McpSyncClient> mcpSyncClients,
      McpStdioClientProperties stdioProperties,
      McpStreamableHttpClientProperties streamableHttpProperties,
      McpClientCommonProperties commonProperties,
      JsonMapper jsonMapper,
      @Value("${spring.ai.mcp.server.version}") String appVersion,
      @Value("${ai.mcp.fetch.max-content-length:8000}") int maxContentLength) {
    McpSyncClient fetchClient =
        mcpSyncClients.stream()
            // Spring AI 连接命名约定：<client-name> - <connection-key>（见 application.yml
            // stdio.connections.fetch）
            .filter(client -> client.getClientInfo().name().endsWith(" - fetch"))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "MCP fetch 连接未建立：请检查 spring.ai.mcp.client.stdio.connections.fetch 配置"));
    McpFetchConnectionFactory connectionFactory =
        new PropertiesMcpFetchConnectionFactory(
            stdioProperties, streamableHttpProperties, commonProperties, jsonMapper, appVersion);
    return new McpExternalToolClient(
        new McpConnectionManager(fetchClient, connectionFactory), maxContentLength);
  }
}
