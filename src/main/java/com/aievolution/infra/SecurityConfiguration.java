package com.aievolution.infra;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 安全接线（W12 #2/#3）：过滤器顺序 = TraceId（HIGHEST_PRECEDENCE）→ 限流（+1）→ 鉴权（+2）。
 *
 * <p>限流排在鉴权前：撞库与无凭证试探同样吃额度，401 不是免费通道； 401/429 拒绝日志均带 traceId，排查"谁的请求被拒"有据可查。
 */
@Configuration
public class SecurityConfiguration {

  @Bean
  public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(
      @Value("${ai.security.rate-limit.per-minute:60}") int maxPerMinute) {
    FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>();
    registration.setFilter(
        new RateLimitFilter(maxPerMinute, () -> System.currentTimeMillis() / 1000));
    registration.addUrlPatterns("/*");
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
    return registration;
  }

  @Bean
  public FilterRegistrationBean<ApiKeyAuthFilter> apiKeyAuthFilter(
      @Value("${ai.security.api-key:}") String apiKey) {
    FilterRegistrationBean<ApiKeyAuthFilter> registration = new FilterRegistrationBean<>();
    registration.setFilter(new ApiKeyAuthFilter(apiKey));
    registration.addUrlPatterns("/*");
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
    return registration;
  }
}
