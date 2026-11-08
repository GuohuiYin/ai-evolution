package com.aievolution.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * JwtRoleConverter 单测（W31 开工冒烟补案）：覆盖人通道 role 声明与机器通道 scope 声明的两种真实形态—— 字符串（空格分隔，测试基建签发形态）与 JSON
 * 数组（Spring Authorization Server 真实签发形态）。 数组形态漏接是 W31 开工冒烟抓到的实账：令牌含 scope 却因零角色被拒 403。
 */
class JwtRoleConverterTest {

  private static Jwt jwt(Map<String, Object> claims) {
    return Jwt.withTokenValue("t")
        .header("alg", "RS256")
        .subject("s")
        .claims(c -> c.putAll(claims))
        .build();
  }

  private static List<String> rolesOf(Jwt jwt) {
    var token = (JwtAuthenticationToken) new JwtRoleConverter().convert(jwt);
    return token.getAuthorities().stream().map(Object::toString).sorted().toList();
  }

  @Test
  void humanChannelRoleClaimMapsToRole() {
    assertThat(rolesOf(jwt(Map.of("role", "admin")))).containsExactly("ROLE_ADMIN");
  }

  @Test
  void scopeClaimAsSpaceSeparatedStringMapsToRoles() {
    assertThat(rolesOf(jwt(Map.of("scope", "writer reader"))))
        .containsExactly("ROLE_READER", "ROLE_WRITER");
  }

  @Test
  void scopeClaimAsJsonArrayMapsToRoles() {
    assertThat(rolesOf(jwt(Map.of("scope", List.of("writer"))))).containsExactly("ROLE_WRITER");
  }

  @Test
  void bothClaimsPresentAreMerged() {
    assertThat(rolesOf(jwt(Map.of("role", "admin", "scope", List.of("writer")))))
        .containsExactly("ROLE_ADMIN", "ROLE_WRITER");
  }

  @Test
  void neitherClaimPresentYieldsNoRoles() {
    assertThat(rolesOf(jwt(Map.of()))).isEmpty();
  }
}
