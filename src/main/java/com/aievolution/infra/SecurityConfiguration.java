package com.aievolution.infra;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 安全接线（W12 #2/#3，W28 #3 增补请求体上限）：过滤器顺序 = TraceId（HIGHEST_PRECEDENCE） → 请求体上限（+1）→ 限流（+2）→ 鉴权（+3）。
 *
 * <p>请求体上限排在限流前：超限大包连限流额度都不该吃，413 是最便宜的拒绝； 排在 TraceId 后：413 拒绝日志带 traceId 可查。限流排在鉴权前：撞库与无凭证试探同样吃额度，
 * 401 不是免费通道；401/413/429 拒绝日志均带 traceId，排查"谁的请求被拒"有据可查。
 */
@Configuration
public class SecurityConfiguration {

  @Bean
  public FilterRegistrationBean<RequestBodySizeLimitFilter> requestBodySizeLimitFilter(
      @Value("${ai.security.max-request-body:32768}") long maxRequestBodyBytes) {
    FilterRegistrationBean<RequestBodySizeLimitFilter> registration =
        new FilterRegistrationBean<>();
    registration.setFilter(new RequestBodySizeLimitFilter(maxRequestBodyBytes));
    registration.addUrlPatterns("/*");
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
    return registration;
  }

  @Bean
  public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(
      @Value("${ai.security.rate-limit.per-minute:60}") int maxPerMinute) {
    FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>();
    registration.setFilter(
        new RateLimitFilter(maxPerMinute, () -> System.currentTimeMillis() / 1000));
    registration.addUrlPatterns("/*");
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
    return registration;
  }

  @Bean
  public FilterRegistrationBean<ApiKeyAuthFilter> apiKeyAuthFilter(
      @Value("${ai.security.api-key:}") String apiKey) {
    FilterRegistrationBean<ApiKeyAuthFilter> registration = new FilterRegistrationBean<>();
    registration.setFilter(new ApiKeyAuthFilter(apiKey));
    registration.addUrlPatterns("/*");
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 3);
    return registration;
  }
}
