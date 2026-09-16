package com.aievolution.infra;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 安全方案（W12 #2b，约定 A7）：文档即契约——所有业务端点声明需要 API Key。
 *
 * <p>两点效果：① {@code /v3/api-docs} 契约中显式声明 apiKey 方案与全局安全要求， 消费者生成客户端时即知凭证位置；② Swagger UI 出现
 * Authorize 按钮，输入 key 后 界面发起的调试请求自动携带 {@code X-API-Key} 头——文档页放行但调试不放行。
 *
 * <p>/mcp 端点不在 OpenAPI 范围（ADR-0009：协议自带 schema），但其鉴权要求同一套 key。
 */
@Configuration
@SecurityScheme(
    name = "apiKey",
    type = SecuritySchemeType.APIKEY,
    in = SecuritySchemeIn.HEADER,
    paramName = "X-API-Key",
    description = "W12 #2 起所有业务端点（/ai/**）需要的 API Key；本地开发值见 .env 的 AI_EVOLUTION_API_KEY")
public class OpenApiSecurityConfiguration {

  /** 全局安全要求：所有已登记端点默认需要 apiKey（无例外清单——业务端点全部持证） */
  @Bean
  public OpenAPI aiEvolutionOpenAPI() {
    return new OpenAPI().addSecurityItem(new SecurityRequirement().addList("apiKey"));
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
