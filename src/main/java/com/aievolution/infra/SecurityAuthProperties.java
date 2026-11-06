package com.aievolution.infra;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 授权策略配置（W29 #1，A11 第二问：端点映射是策略不是常量；W30 #2 起纯授权配置）。
 *
 * <ul>
 *   <li>{@code endpointRoles}：端点→角色映射。未列路径 = 已认证即可；公开面（免认证）见 {@link SecurityAllowlist} 单点。空表启动
 *       fail-fast——安全策略不允许静默为空
 *   <li>角色来源：JWT 声明（人通道 {@code role} / 机器通道 {@code scope}），映射见 {@link JwtRoleConverter}； 原 {@code
 *       keyRoles} 字段随 API Key 凭证退役（W30 #2 直切）
 * </ul>
 */
@ConfigurationProperties(prefix = "ai.security")
public record SecurityAuthProperties(List<EndpointRole> endpointRoles) {

  /** 一条端点授权规则：HTTP 方法 + Ant 路径模式 + 所需角色（不含 ROLE_ 前缀） */
  public record EndpointRole(String method, String pattern, String role) {}
}
