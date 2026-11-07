package com.aievolution.authcenter.protocol;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

/**
 * W30 #1：双安全链手工装配——Boot 对授权服务器的自动装配是「全有或全无」 （ConditionalOnDefaultWebSecurity，自定义任一链则双链同时退出），为放行
 * actuator 健康探针（K8s 探针不带凭证，C1）而整体接管。
 *
 * <p>链 1 = 授权服务器协议端点（/oauth2/**、/.well-known/** 由 configurer 的端点匹配器圈定）； 链 2 =
 * 默认链：健康探针放行，其余一律要求登录（formLogin 为步骤 3 人通道登录页打底）。
 */
@Configuration
class AuthorizationServerSecurityConfiguration {

  @Bean
  @Order(1)
  SecurityFilterChain authorizationServerSecurityFilterChain(
      HttpSecurity http, @Value("${auth.cors.allowed-origins:}") String corsAllowedOrigins)
      throws Exception {
    OAuth2AuthorizationServerConfigurer authorizationServer =
        new OAuth2AuthorizationServerConfigurer();
    return http.securityMatcher(authorizationServer.getEndpointsMatcher())
        .with(authorizationServer, Customizer.withDefaults())
        // W30 #3：SPA 跨域换 token 的预检由链内 CorsFilter 在鉴权前处理（deny-by-default，白名单来自配置）
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
                      config.setAllowedMethods(java.util.List.of("GET", "POST", "OPTIONS"));
                      config.setAllowedHeaders(java.util.List.of("Authorization", "Content-Type"));
                      config.setMaxAge(3600L);
                      return config;
                    }))
        .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
        .exceptionHandling(
            exceptions ->
                exceptions.defaultAuthenticationEntryPointFor(
                    new LoginUrlAuthenticationEntryPoint("/login"),
                    new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
        .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()))
        .build();
  }

  @Bean
  @Order(2)
  SecurityFilterChain defaultSecurityFilterChain(
      HttpSecurity http, @Value("${auth.cors.allowed-origins:}") String corsAllowedOrigins)
      throws Exception {
    return http.authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        // W30 #3：预检 OPTIONS /oauth2/token 不匹配链 1 的端点匹配器（token 端点按方法圈定，
        // OPTIONS 落进本链）——CORS 白名单双链同挂，预检由链内 CorsFilter 在鉴权前放行
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
                      config.setAllowedMethods(java.util.List.of("GET", "POST", "OPTIONS"));
                      config.setAllowedHeaders(java.util.List.of("Authorization", "Content-Type"));
                      config.setMaxAge(3600L);
                      return config;
                    }))
        .formLogin(Customizer.withDefaults())
        .build();
  }
}
