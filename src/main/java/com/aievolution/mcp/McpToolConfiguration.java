package com.aievolution.mcp;

import com.aievolution.tool.AnnouncementTools;
import com.aievolution.tool.StockDataTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP 接入层配置：把 tool 域现有 @Tool 方法注册为 MCP tools（W6-Step2）。
 *
 * <p>本域只做"协议暴露"，不含任何业务逻辑——三个工具的 schema 与执行完全复用 tool 域， 这是对约定 A10（跨域面向接口）的实战检验：抽象干净则此处零业务代码。
 *
 * <p>注意：Spring AI MCP Server 自动装配会收集所有 {@link ToolCallbackProvider} Bean 注册为 MCP tools；方法上的 @Tool
 * 描述/参数说明即工具的"模型使用手册"，同源复用。
 */
@Configuration
public class McpToolConfiguration {

  @Bean
  public ToolCallbackProvider mcpTools(
      StockDataTools stockDataTools, AnnouncementTools announcementTools) {
    return MethodToolCallbackProvider.builder()
        .toolObjects(stockDataTools, announcementTools)
        .build();
  }
}
