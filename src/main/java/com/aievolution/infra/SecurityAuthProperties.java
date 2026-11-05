package com.aievolution.infra;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 认证授权策略配置（W29 #1，A11 第二问：角色与端点映射是策略不是常量）。
 *
 * <ul>
 *   <li>{@code keyRoles}：API key 身份授予的角色表。单 Owner 时代一把 key = admin 全角色； W30 接 JWT 后角色来源切换为
 *       scope，本字段随之退役
 *   <li>{@code endpointRoles}：端点→角色映射。未列路径 = 已认证即可；公开面（免认证）见 {@link SecurityAllowlist} 单点。两项皆空即启动
 *       fail-fast——安全策略不允许静默为空
 * </ul>
 */
@ConfigurationProperties(prefix = "ai.security")
public record SecurityAuthProperties(List<String> keyRoles, List<EndpointRole> endpointRoles) {

  /** 一条端点授权规则：HTTP 方法 + Ant 路径模式 + 所需角色（不含 ROLE_ 前缀） */
  public record EndpointRole(String method, String pattern, String role) {}
}
