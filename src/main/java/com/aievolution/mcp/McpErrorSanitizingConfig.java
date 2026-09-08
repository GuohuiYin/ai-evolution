package com.aievolution.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.modelcontextprotocol.spec.McpError;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP 错误脱敏（W7-Step1，红队基线 P0）：MCP 传输层把 {@link McpError} 直接作为响应体返回， Jackson 默认序列化 Throwable
 * 全家桶（cause/stackTrace/suppressed/localizedMessage）—— 类名、文件名、行号全暴露，攻击者的免费侦察报告。
 *
 * <p>修复点选在序列化层而非异常处理层：传输层"捕获异常→直接 ResponseEntity 返回"， 不经过 DispatcherServlet 的 @ExceptionHandler
 * 链（第一次尝试已实证无效）。
 *
 * <p>脱敏语义：对外保留 message 与 jsonRpcError（协议字段，含 code，客户端需要）， 屏蔽
 * cause/stackTrace/suppressed/localizedMessage（内部实现细节）。 完整堆栈由服务端日志按 traceId 关联排障，内外分离。
 *
 * <p>技术注记：Boot 4 已升级 Jackson 3，定制器为 {@link JsonMapperBuilderCustomizer}。
 */
@Configuration
public class McpErrorSanitizingConfig {

  /** 只序列化白名单字段：message + jsonRpcError。其余 Throwable 结构全部屏蔽。 */
  @JsonIgnoreProperties({"cause", "stackTrace", "suppressed", "localizedMessage"})
  abstract static class McpErrorMixIn {}

  @Bean
  public JsonMapperBuilderCustomizer mcpErrorSanitizer() {
    return builder -> builder.addMixIn(McpError.class, McpErrorMixIn.class);
  }
}
