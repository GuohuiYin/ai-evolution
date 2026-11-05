package com.aievolution.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * 401/403 统一出口（W29 #1）：认证失败与授权不足自此分清，两者皆走 RFC 7807 ProblemDetail（B3）。
 *
 * <ul>
 *   <li>401：无凭证或凭证非法（认证失败），附 {@code WWW-Authenticate: Bearer} 引导正史通道
 *   <li>403：凭证合法但角色不足（授权失败）——单 key 时代日常不触发，多角色/W30 scope 落地后生效
 * </ul>
 */
public class SecurityProblemDetailHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    // 头必须先于写体：writeValue 即提交响应，之后再设头会被静默丢弃（单测锁定）
    response.setHeader("WWW-Authenticate", "Bearer");
    write(
        response,
        HttpStatus.UNAUTHORIZED,
        "缺少或非法凭证（请使用 Authorization: Bearer 头；X-API-Key 兼容期内仍可用，W30 退役）");
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    write(response, HttpStatus.FORBIDDEN, "凭证合法但角色不足，无权访问该端点");
  }

  private static void write(HttpServletResponse response, HttpStatus status, String detail)
      throws IOException {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    OBJECT_MAPPER.writeValue(response.getWriter(), problem);
  }
}
