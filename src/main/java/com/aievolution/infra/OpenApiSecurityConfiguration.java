package com.aievolution.infra;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.security.SecuritySchemes;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 安全方案（W12 #2b，约定 A7；W29 #1 切 Bearer 正史）：文档即契约——业务端点声明 Bearer 认证。
 *
 * <p>两点效果：① {@code /v3/api-docs} 契约显式声明安全方案与全局安全要求， 消费者生成客户端时即知凭证位置；② Swagger UI 出现 Authorize 按钮，输入
 * token 后界面发起的调试请求自动携带 {@code Authorization: Bearer} 头——文档页放行但调试不放行。
 *
 * <p>W29 #1 兼容期（Owner 裁决 A）：Bearer 为正史，X-API-Key 继续可用但标 deprecated， 契约以两个 security item
 * 表达「二选一」（OpenAPI 同一 item 内多 scheme 是 AND，两个 item 是 OR）； W30 接 JWT 时 X-API-Key 声明随兼容期结束移除。
 *
 * <p>/mcp 端点不在 OpenAPI 范围（ADR-0009：协议自带 schema），但其鉴权要求同一套凭证。
 */
@Configuration
@SecuritySchemes({
  @SecurityScheme(
      name = "bearer",
      type = SecuritySchemeType.HTTP,
      scheme = "bearer",
      description =
          "W29 #1 起正史：Authorization: Bearer <key>；本地开发值见 .env 的 AI_EVOLUTION_API_KEY（W30 起为认证中心签发的 JWT）"),
  @SecurityScheme(
      name = "apiKey",
      type = SecuritySchemeType.APIKEY,
      in = SecuritySchemeIn.HEADER,
      paramName = "X-API-Key",
      description = "已废弃（deprecated）：W29 #1 兼容期保留，请迁移到 Bearer；W30 退役")
})
public class OpenApiSecurityConfiguration {

  /** 全局安全要求：Bearer 或 X-API-Key 二选一（两个 security item = OR 语义） */
  @Bean
  public OpenAPI aiEvolutionOpenAPI() {
    return new OpenAPI()
        .addSecurityItem(new SecurityRequirement().addList("bearer"))
        .addSecurityItem(new SecurityRequirement().addList("apiKey"));
  }

  /** W12 #3（A7 全部错误码）：每个端点统一补 429 声明——限流是横切行为，不该逐控制器手抄 */
  @Bean
  public OpenApiCustomizer tooManyRequestsCustomizer() {
    return openApi ->
        openApi
            .getPaths()
            .values()
            .forEach(
                pathItem ->
                    pathItem
                        .readOperations()
                        .forEach(
                            operation ->
                                operation
                                    .getResponses()
                                    .addApiResponse(
                                        "429",
                                        new ApiResponse()
                                            .description(
                                                "请求频率超限（每身份每分钟限额；RFC 7807 ProblemDetail + Retry-After 头）"))));
  }
}
