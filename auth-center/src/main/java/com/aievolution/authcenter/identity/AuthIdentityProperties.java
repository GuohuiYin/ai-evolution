package com.aievolution.authcenter.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * identity 域配置（auth.identity.*）：admin 种子。哈希由 PasswordHashTool 离线生成 （{argon2} 前缀格式），env
 * 注入——密码明文不过夜（2026-11-05 Owner 裁决候选 B）。
 */
@ConfigurationProperties(prefix = "auth.identity")
public record AuthIdentityProperties(String adminUsername, String adminPasswordHash) {}
