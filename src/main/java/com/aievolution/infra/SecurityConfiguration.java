package com.aievolution.infra;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 安全接线（W12 #2/#3 → W28 #3 增补请求体上限 → W29 #1 收口进 Spring Security 链 → W30 #2 切换 JWT 资源服务器）。
 *
 * <p>过滤器顺序 = TraceId（HIGHEST_PRECEDENCE）→ 请求体上限（+1）→ 限流（+2）→ Spring Security 链 （认证：Bearer JWT 由
 * auth-center JWKS 公钥验签 → 身份+角色；授权：端点→角色映射；401/403 统一 ProblemDetail）。
 *
 * <p>请求体上限排在限流前：超限大包连限流额度都不该吃，413 是最便宜的拒绝； 排在 TraceId 后：413 拒绝日志带 traceId 可查。限流排在鉴权前：撞库与无凭证试探同样吃额度，
 * 401 不是免费通道；401/403/413/429 拒绝日志均带 traceId，排查"谁的请求被拒"有据可查。
 */
@Configuration
@EnableConfigurationProperties(SecurityAuthProperties.class)
public class SecurityConfiguration {

  /**
   * 角色层级（W29 #1）：admin ⊇ writer ⊇ reader——admin 不再逐端点重复列举下层角色。 授权规则只需声明该端点的最低角色，层级自动向下兼容（W30 #2
   * 起角色来自 JWT 声明）
   */
  @Bean
  public org.springframework.security.access.hierarchicalroles.RoleHierarchy roleHierarchy() {
    return org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl.fromHierarchy(
        "ROLE_ADMIN > ROLE_WRITER > ROLE_READER");
  }

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

  /**
   * 认证授权主链（W30 #2）：凭证认证由 Spring Security 标准 JWT 资源服务器承担。
   *
   * <ul>
   *   <li>认证：{@code Authorization: Bearer <JWT>}——auth-center（统一认证中心）RS256 签发， 本服务凭 JWKS
   *       公钥验签（{@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri}）； opaque Bearer 与
   *       X-API-Key 已于本任务同批退役（Owner 裁决：直切）
   *   <li>角色：{@link JwtRoleConverter}——人通道 {@code role} 声明 / 机器通道 {@code scope} 声明统一折成 {@code
   *       ROLE_*}，端点角色表与层级沿用 W29 不变
   *   <li>授权：公开面（{@link SecurityAllowlist}）permitAll → 端点角色表（配置化）→ 其余已认证即可
   *   <li>401/403：{@link SecurityProblemDetailHandler} 统一 ProblemDetail（同时挂到资源服务器入口点， 非法 JWT 的 401
   *       同样是 ProblemDetail），401 附 WWW-Authenticate: Bearer
   *   <li>CSRF 关闭：纯无状态 API、无 Cookie 会话，CSRF 无从附着； 安全头套件见 W29 #2（X-Frame-Options=DENY /
   *       Referrer-Policy=no-referrer / CSP 自托管白名单）
   * </ul>
   */
  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      @Value("${ai.security.cors.allowed-origins:}") String corsAllowedOrigins,
      SecurityAuthProperties authProperties)
      throws Exception {
    if (authProperties.endpointRoles() == null || authProperties.endpointRoles().isEmpty()) {
      throw new IllegalStateException("ai.security.endpoint-roles 为空：端点角色映射是安全策略，不允许静默为空（W29 #1）");
    }
    var problemHandler = new SecurityProblemDetailHandler();
    http.authorizeHttpRequests(
            auth -> {
              SecurityAllowlist.publicPaths()
                  .forEach(path -> auth.requestMatchers(path).permitAll());
              authProperties
                  .endpointRoles()
                  .forEach(
                      mapping ->
                          auth.requestMatchers(
                                  HttpMethod.valueOf(mapping.method()), mapping.pattern())
                              .hasRole(mapping.role().toUpperCase()));
              auth.anyRequest().authenticated();
            })
        .csrf(csrf -> csrf.disable())
        // 无状态会话（W29 #1）：凭证头直连的纯 API 不该发 JSESSIONID——
        // 默认 IF_REQUIRED 会在 401 时建会话发 Cookie，与「无 Cookie 会话」的 CSRF 论证自相矛盾
        .sessionManagement(
            session ->
                session.sessionCreationPolicy(
                    org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
        // W29 #3 CORS 显式策略：deny-by-default，白名单来自配置（空 = 拒绝一切跨域；
        // 预检 OPTIONS 由链内 CorsFilter 在鉴权前处理，合法跨域不被 401 误伤）
        .cors(
            cors ->
                cors.configurationSource(
                    request -> {
                      var config = new org.springframework.web.cors.CorsConfiguration();
                      config.setAllowedOrigins(
                          java.util.Arrays.stream(corsAllowedOrigins.split(","))
                              .map(String::trim)
                              .filter(origin -> !origin.isEmpty())
                              .toList());
                      config.setAllowedMethods(
                          java.util.List.of("GET", "POST", "DELETE", "OPTIONS"));
                      config.setAllowedHeaders(java.util.List.of("Authorization", "Content-Type"));
                      config.setMaxAge(3600L);
                      return config;
                    }))
        // W29 #2 安全响应头套件（走 Spring Security 头组件，不手写）：
        // X-Content-Type-Options=nosniff / Cache-Control / HSTS(仅HTTPS) 为框架默认值；
        // X-Frame-Options=DENY 防点击劫持；Referrer-Policy=no-referrer 防 URL 参数外泄；
        // CSP 自托管白名单——对话页与 Swagger UI 均为同源资源+内联脚本/样式（unsafe-inline
        // 是内联结构的过渡态，抽外置文件后收紧，见头注释）
        .headers(
            headers ->
                headers
                    .frameOptions(frame -> frame.deny())
                    .referrerPolicy(
                        referrer ->
                            referrer.policy(
                                org.springframework.security.web.header.writers
                                    .ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                    .contentSecurityPolicy(
                        csp ->
                            csp.policyDirectives(
                                "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'")))
        // W30 #2：JWT 资源服务器（验签 + 角色映射）；401/403 出口与全局 exceptionHandling 同一个
        // ProblemDetail 处理器——非法 token 的拒绝语义与缺凭证、角色不足保持一致形态
        .oauth2ResourceServer(
            resourceServer ->
                resourceServer
                    .authenticationEntryPoint(problemHandler)
                    .accessDeniedHandler(problemHandler)
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(new JwtRoleConverter())))
        .exceptionHandling(
            handling ->
                handling
                    .authenticationEntryPoint(problemHandler)
                    .accessDeniedHandler(problemHandler));
    return http.build();
  }
}
