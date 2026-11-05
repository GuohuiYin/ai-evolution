package com.aievolution.authcenter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 统一认证授权中心（W30 #1）：独立部署的签发端——授权码+PKCE（人）/ 客户端凭证（机/MCP）， JWT 签发 + JWKS 公钥端点。ai-evolution
 * 退化为资源服务器（只验票不认证）。设计见 docs/security/w30-auth-center-design.md。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AuthCenterApplication {

  public static void main(String[] args) {
    SpringApplication.run(AuthCenterApplication.class, args);
  }
}
