package com.aievolution.infra;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * JWT → 角色映射（W30 #2）：认证中心两通道的声明形态统一折成 Spring Security 角色。
 *
 * <ul>
 *   <li>人通道（授权码 + PKCE）：{@code role} 单值声明（auth-center 注入，见 W30 #1 步骤 3b）
 *   <li>机器通道（client_credentials）：{@code scope} 声明——两种真实形态都接：空格分隔字符串 （OAuth2 标准形态，本仓库测试基建按此签发）与 JSON
 *       数组（Spring Authorization Server 真实签发形态， W31 开工冒烟实账：数组形态被按字符串读会得到 {@code "[writer]"}
 *       这样的伪角色，令牌含 scope 仍零角色 403）
 *   <li>统一映射 {@code ROLE_<大写>}：W29 端点角色表与 RoleHierarchy（admin⊇writer⊇reader）零改动
 *   <li>两声明皆缺 → 零角色：已认证但访问受角色保护的端点得 403（授权失败），不混淆为 401（认证失败）
 * </ul>
 */
public class JwtRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    Set<String> roles = new LinkedHashSet<>();
    String role = jwt.getClaimAsString("role");
    if (role != null && !role.isBlank()) {
      roles.add(role.trim());
    }
    Object scopeClaim = jwt.getClaims().get("scope");
    if (scopeClaim instanceof String scope) {
      for (String s : scope.split(" ")) {
        if (!s.isBlank()) {
          roles.add(s.trim());
        }
      }
    } else if (scopeClaim instanceof java.util.Collection<?> scopes) {
      for (Object s : scopes) {
        if (s != null && !s.toString().isBlank()) {
          roles.add(s.toString().trim());
        }
      }
    }
    List<SimpleGrantedAuthority> authorities =
        roles.stream()
            .map(r -> new SimpleGrantedAuthority("ROLE_" + r.toUpperCase(Locale.ROOT)))
            .toList();
    return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
  }
}
