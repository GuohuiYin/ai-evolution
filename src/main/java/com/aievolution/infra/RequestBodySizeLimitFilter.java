package com.aievolution.infra;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * W28 #3 请求体上限（server 侧第一道）：Content-Length 超上限即 413 拒绝， 请求不进入限流/鉴权/业务链——超限请求连额度都不该吃（同限流排在鉴权前的哲学）。
 *
 * <p>职责切分：本过滤器管「整个请求体」的传输层上限（防大包冲击解析层）； 业务字段级上限（如 message ≤2000 字符）由 {@code ChatRequest} 的
 * {@code @Size} 承担 （Bean Validation 层）。无 Content-Length 的请求（chunked）不在本层拦， 由字段级校验兜底。超限额配置化：{@code
 * ai.security.max-request-body}（A11-2）。
 */
public class RequestBodySizeLimitFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(RequestBodySizeLimitFilter.class);

  private final long maxBytes;

  public RequestBodySizeLimitFilter(long maxBytes) {
    this.maxBytes = maxBytes;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    long contentLength = request.getContentLengthLong();
    if (contentLength > maxBytes) {
      // 413 Content Too Large + RFC 7807（B3）：拒绝细节不外泄内部上限值以外的信息
      log.warn(
          "请求体超限拒绝: contentLength={} limit={} path={}",
          contentLength,
          maxBytes,
          request.getRequestURI());
      response.setStatus(413);
      response.setContentType("application/problem+json");
      response
          .getWriter()
          .write(
              "{\"title\":\"请求体超过大小上限\",\"status\":413,\"error\":\"request_too_large\",\"limitBytes\":"
                  + maxBytes
                  + "}");
      return;
    }
    filterChain.doFilter(request, response);
  }
}
