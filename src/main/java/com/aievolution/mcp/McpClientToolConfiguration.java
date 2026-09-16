package com.aievolution.mcp;

import com.aievolution.tool.ExternalToolClient;
import io.modelcontextprotocol.client.McpSyncClient;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP Client 工具接线（W12 #1b）：从自动装配的 sync client 列表中挑出 fetch 连接， 封装为 tool 域 {@link ExternalToolClient}
 * 契约 Bean——协议选型与连接识别不出 mcp 域。
 *
 * <p>仅在 MCP Client 启用时注册（测试上下文关闭，不起 uvx 子进程）；启用而连接缺失 属于配置错误，启动 fail-fast 而非运行期才发现。
 */
@Configuration
@ConditionalOnProperty(name = "spring.ai.mcp.client.enabled", havingValue = "true")
public class McpClientToolConfiguration {

  @Bean
  public ExternalToolClient externalToolClient(
      List<McpSyncClient> mcpSyncClients,
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
    return new McpExternalToolClient(fetchClient, maxContentLength);
  }
}
