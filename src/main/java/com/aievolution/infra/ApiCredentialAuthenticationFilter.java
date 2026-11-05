package com.aievolution.infra;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * API 凭证认证过滤器（W29 #1，收口 {@code ApiKeyAuthFilter} 进 Spring Security 链）。
 *
 * <p>设计决策：
 *
 * <ul>
 *   <li><b>Bearer 正史</b>：{@code Authorization: Bearer <key>} 优先；{@code X-API-Key} 为兼容期副通道（Owner 裁决
 *       A，2026-11-05），首次使用打 WARN 留痕，W30 接 JWT 时退役
 *   <li>比对走常量时间（{@link MessageDigest#isEqual}），防时序侧信道（沿用 W12 #2 纪律）
 *   <li>本过滤器只认证不授权：凭证合法 → SecurityContext 置入身份+角色；缺失/非法 → 不置身份， 由链上授权规则拒绝，401 由
 *       AuthenticationEntryPoint 统一写（401/403 语义自此分清）
 *   <li>角色来自配置 {@code ai.security.key-roles}（单 Owner 时代为 admin），不硬编码
 * </ul>
 */
public class ApiCredentialAuthenticationFilter extends OncePerRequestFilter {

  /**
   * @deprecated 兼容期副通道，W30 接 JWT 时退役（Owner 裁决 A）
   */
  @Deprecated public static final String API_KEY_HEADER = "X-API-Key";

  public static final String BEARER_PREFIX = "Bearer ";

  private static final Logger log =
      LoggerFactory.getLogger(ApiCredentialAuthenticationFilter.class);

  private final byte[] expectedKey;
  private final List<SimpleGrantedAuthority> authorities;
  private final AtomicBoolean legacyHeaderWarned = new AtomicBoolean(false);

  public ApiCredentialAuthenticationFilter(String apiKey, List<String> keyRoles) {
    if (apiKey == null || apiKey.isBlank()) {
      // 配置错误 fail-fast（A11）：宁可启动失败，不可带病裸奔
      throw new IllegalStateException(
          "ai.security.api-key 未配置：API Key 鉴权为强制项（W12 #2），请设置环境变量 AI_EVOLUTION_API_KEY");
    }
    if (keyRoles == null || keyRoles.isEmpty()) {
      throw new IllegalStateException("ai.security.key-roles 为空：凭证合法却无角色等于全拒，安全策略不允许静默为空（W29 #1）");
    }
    this.expectedKey = apiKey.getBytes(StandardCharsets.UTF_8);
    this.authorities =
        keyRoles.stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.trim().toUpperCase()))
            .toList();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String credential = extractCredential(request);
    if (credential != null
        && MessageDigest.isEqual(expectedKey, credential.getBytes(StandardCharsets.UTF_8))) {
      var authentication =
          UsernamePasswordAuthenticationToken.authenticated("api-key-owner", null, authorities);
      SecurityContextHolder.getContext().setAuthentication(authentication);
    }
    filterChain.doFilter(request, response);
  }

  /** 凭证提取：Bearer 正史优先；X-API-Key 兼容期兜底（首次使用 WARN 有声化） */
  private String extractCredential(HttpServletRequest request) {
    String authorization = request.getHeader("Authorization");
    if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
      return authorization.substring(BEARER_PREFIX.length());
    }
    String legacy = request.getHeader(API_KEY_HEADER);
    if (legacy != null && legacyHeaderWarned.compareAndSet(false, true)) {
      log.warn("X-API-Key 兼容通道首次被使用：请迁移到 Authorization: Bearer（W29 #1 裁决 A，W30 退役）");
    }
    return legacy;
  }
}
