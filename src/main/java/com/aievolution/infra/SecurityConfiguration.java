package com.aievolution.infra;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

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

  /**
   * Spring Security 过渡链（W29 #1 第一步）：全放行 + 关 CSRF + 关头套件，行为与裸 Filter 链逐字节一致。
   *
   * <p>存在的唯一理由是压制自动装配——spring-boot-starter-security 上类路径后，默认链会开表单登录/CSRF/安全头， 直接打挂现有端点与测试。本 bean
   * 显式接管后，鉴权仍由 {@link ApiKeyAuthFilter} 承担，行为零变化； 收口（RBAC/Bearer 映射进本链）与头套件启用是 W29 #1/#2
   * 的后续步，届时本注释同步更新。
   *
   * <p>顺序：本链（Security 默认 -100）排在四道裸过滤器（HIGHEST_PRECEDENCE+0..3）之后， 即 TraceId → 413 → 限流 → 鉴权 →
   * 本链（过渡放行）→ Controller。
   */
  @Bean
  public SecurityFilterChain w29TransitionalSecurityFilterChain(HttpSecurity http)
      throws Exception {
    http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .csrf(csrf -> csrf.disable())
        .headers(headers -> headers.disable());
    return http.build();
  }
}
