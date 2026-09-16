package com.aievolution.infra;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 安全接线（W12 #2）：API Key 过滤器全路径注册（/*），放行逻辑在过滤器内部显式声明。
 *
 * <p>顺序约定：排在 {@link TraceIdFilter}（HIGHEST_PRECEDENCE）之后——401 拒绝日志也带 traceId， 排查"谁的请求被拒"有据可查。
 */
@Configuration
public class SecurityConfiguration {

  @Bean
  public FilterRegistrationBean<ApiKeyAuthFilter> apiKeyAuthFilter(
      @Value("${ai.security.api-key:}") String apiKey) {
    FilterRegistrationBean<ApiKeyAuthFilter> registration = new FilterRegistrationBean<>();
    registration.setFilter(new ApiKeyAuthFilter(apiKey));
    registration.addUrlPatterns("/*");
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
    return registration;
  }
}
