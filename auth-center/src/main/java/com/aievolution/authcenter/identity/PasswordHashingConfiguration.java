package com.aievolution.authcenter.identity;

import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码编码策略（W30 #1）：argon2 为默认编码（人类密码慢哈希，抗暴力破解）；{noop} 仅服务机器 密钥（高熵随机串无需慢哈希，如 mcp-client 的
 * client_secret）。显式构造编码表而非工厂 默认——编码表即安全策略，显式声明（ADR-0013 显式配置精神）。
 */
@Configuration
class PasswordHashingConfiguration {

  @Bean
  PasswordEncoder passwordEncoder() {
    return new DelegatingPasswordEncoder(
        "argon2",
        Map.of(
            "argon2", Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8(),
            "noop", NoOpPasswordEncoder.getInstance()));
  }
}
