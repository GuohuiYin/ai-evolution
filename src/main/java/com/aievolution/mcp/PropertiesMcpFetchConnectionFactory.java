package com.aievolution.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpClientCommonProperties;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStdioClientProperties;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStreamableHttpClientProperties;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link McpFetchConnectionFactory} 的配置属性实现：复用 Spring AI 自动装配绑定的同一份连接属性
 * （spring.ai.mcp.client.*，不另起配置键——单一事实源，重建出的连接与启动期连接同参）。
 *
 * <p>传输选择与部署形态约定一致：stdio（本地 default profile）优先，其次 streamable-http（k8s
 * profile）；两者都没有即配置错误，fail-fast。
 */
public class PropertiesMcpFetchConnectionFactory implements McpFetchConnectionFactory {

  /** 连接键与 application.yml 的 connections.fetch 段对应（协议字面量） */
  static final String CONNECTION_KEY = "fetch";

  private final McpStdioClientProperties stdioProperties;
  private final McpStreamableHttpClientProperties httpProperties;
  private final McpClientCommonProperties commonProperties;
  private final JsonMapper jsonMapper;
  private final String appVersion;

  public PropertiesMcpFetchConnectionFactory(
      McpStdioClientProperties stdioProperties,
      McpStreamableHttpClientProperties httpProperties,
      McpClientCommonProperties commonProperties,
      JsonMapper jsonMapper,
      String appVersion) {
    this.stdioProperties = stdioProperties;
    this.httpProperties = httpProperties;
    this.commonProperties = commonProperties;
    this.jsonMapper = jsonMapper;
    this.appVersion = appVersion;
  }

  @Override
  public McpSyncClient connect() {
    // 命名沿用 Spring AI 约定 <client-name> - <connection-key>：审计与日志里重建连接与启动期连接同形
    McpSyncClient client =
        McpClient.sync(newTransport())
            .requestTimeout(commonProperties.getRequestTimeout())
            .clientInfo(
                new Implementation(commonProperties.getName() + " - " + CONNECTION_KEY, appVersion))
            .build();
    client.initialize();
    return client;
  }

  /** 传输构建（纯逻辑，单测覆盖三分支）；拉起子进程/网络握手发生在 initialize，不在此 */
  McpClientTransport newTransport() {
    var stdio = stdioProperties.getConnections().get(CONNECTION_KEY);
    if (stdio != null) {
      return new StdioClientTransport(
          stdio.toServerParameters(), new JacksonMcpJsonMapper(jsonMapper));
    }
    var http = httpProperties.getConnections().get(CONNECTION_KEY);
    if (http != null) {
      var builder =
          HttpClientStreamableHttpTransport.builder(http.url())
              .jsonMapper(new JacksonMcpJsonMapper(jsonMapper));
      if (http.endpoint() != null && !http.endpoint().isBlank()) {
        builder = builder.endpoint(http.endpoint());
      }
      return builder.build();
    }
    throw new IllegalStateException(
        "MCP fetch 连接未配置：stdio 与 streamable-http 均无 connections." + CONNECTION_KEY + " 段");
  }
}
