package com.aievolution.infra;

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
 * OpenAPI 安全方案（W12 #2b，约定 A7；W30 #2 切 JWT）：文档即契约——业务端点声明 Bearer JWT 认证。
 *
 * <p>两点效果：① {@code /v3/api-docs} 契约显式声明安全方案与全局安全要求， 消费者生成客户端时即知凭证位置；② Swagger UI 出现 Authorize 按钮，输入
 * token 后界面发起的调试请求自动携带 {@code Authorization: Bearer} 头——文档页放行但调试不放行。
 *
 * <p>W30 #2（Owner 裁决：直切）：凭证即统一认证中心（auth-center）签发的 JWT， opaque Bearer 与 X-API-Key 方案声明同批移除。
 *
 * <p>/mcp 端点不在 OpenAPI 范围（ADR-0009：协议自带 schema），但其鉴权要求同一套凭证。
 */
@Configuration
@SecuritySchemes({
  @SecurityScheme(
      name = "bearer",
      type = SecuritySchemeType.HTTP,
      scheme = "bearer",
      bearerFormat = "JWT",
      description = "Authorization: Bearer <JWT>，由统一认证中心（auth-center）签发；本服务凭 JWKS 公钥验签（W30 #2）")
})
public class OpenApiSecurityConfiguration {

  /** 全局安全要求：Bearer JWT */
  @Bean
  public OpenAPI aiEvolutionOpenAPI() {
    return new OpenAPI().addSecurityItem(new SecurityRequirement().addList("bearer"));
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
