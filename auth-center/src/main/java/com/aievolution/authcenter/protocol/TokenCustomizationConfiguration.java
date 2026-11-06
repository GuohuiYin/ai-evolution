package com.aievolution.authcenter.protocol;

import com.aievolution.authcenter.identity.UserAccountRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

/**
 * JWT role 声明（W30 #1）：人通道令牌携带用户角色（来源=用户表，单一事实源），供 #2 资源服务器做 RBAC 映射；机器客户端名不在用户表，天然不带 role 声明（人机分离）。
 */
@Configuration
class TokenCustomizationConfiguration {

  @Bean
  OAuth2TokenCustomizer<JwtEncodingContext> userRoleClaim(UserAccountRepository users) {
    return context ->
        users
            .findByUsername(context.getPrincipal().getName())
            .ifPresent(
                account -> context.getClaims().claim("role", account.role().name().toLowerCase()));
  }
}
