package com.aievolution.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * W29 #1：API 凭证认证行为锁定——Bearer 正史 / X-API-Key 兼容 / 配置 fail-fast。
 *
 * <p>401/403 语义与端点角色映射由链级测试另行锁定（本类只管"凭证→身份+角色"这一段）。
 */
class ApiCredentialAuthenticationFilterTest {

  private static final String KEY = "test-key";

  private final ApiCredentialAuthenticationFilter filter =
      new ApiCredentialAuthenticationFilter(KEY, List.of("admin"));

  @Test
  void blankApiKeyFailsFast() {
    assertThatThrownBy(() -> new ApiCredentialAuthenticationFilter(" ", List.of("admin")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("AI_EVOLUTION_API_KEY");
  }

  @Test
  void emptyKeyRolesFailsFast() {
    assertThatThrownBy(() -> new ApiCredentialAuthenticationFilter(KEY, List.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("key-roles");
  }

  @Test
  void bearerTokenAuthenticatesWithConfiguredRoles() throws ServletException, IOException {
    SecurityContextHolder.clearContext();
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ai/chat");
    request.addHeader("Authorization", "Bearer " + KEY);

    filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

    var authentication = SecurityContextHolder.getContext().getAuthentication();
    assertThat(authentication).isNotNull();
    assertThat(
            authentication.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority))
        .containsExactly("ROLE_ADMIN");
  }

  @Test
  void legacyApiKeyHeaderStillAcceptedDuringCompatWindow() throws ServletException, IOException {
    SecurityContextHolder.clearContext();
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ai/chat");
    request.addHeader(ApiCredentialAuthenticationFilter.API_KEY_HEADER, KEY);

    filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
  }

  @Test
  void wrongCredentialLeavesRequestAnonymous() throws ServletException, IOException {
    SecurityContextHolder.clearContext();
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ai/chat");
    request.addHeader("Authorization", "Bearer wrong-key");

    filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

    // 不置身份、不在过滤器内拒绝——401 由链上 EntryPoint 统一写（职责切分）
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void missingCredentialPassesThroughAnonymously() throws ServletException, IOException {
    SecurityContextHolder.clearContext();

    filter.doFilter(
        new MockHttpServletRequest("GET", "/ai/cost/summary"),
        new MockHttpServletResponse(),
        new MockFilterChain());

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }
}
