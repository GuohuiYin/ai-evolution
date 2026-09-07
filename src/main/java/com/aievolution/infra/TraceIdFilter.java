package com.aievolution.infra;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * TraceId 过滤器（可观测性基座）：每个请求一个 traceId，全链路日志按它串联。
 *
 * <p>语义四条：
 *
 * <ul>
 *   <li>透传：上游带 {@code X-Trace-Id} 则沿用（未来接网关/多级服务时链不断）
 *   <li>生成：缺省时生成短 UUID
 *   <li>回写：响应头带回 traceId，用户报障可直接给号
 *   <li>清理：finally 中清 MDC——线程池复用场景下防串号
 * </ul>
 *
 * <p>安全：入站 traceId 需过白名单校验（防日志注入伪造行），非法则当作缺省重新生成。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

  public static final String TRACE_ID_HEADER = "X-Trace-Id";
  public static final String MDC_KEY = "traceId";

  /** 合法 traceId：字母数字/连字符，最长 64——够上游系统用，堵死换行/控制字符注入。 */
  private static final Pattern VALID = Pattern.compile("[A-Za-z0-9\\-]{1,64}");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String traceId = request.getHeader(TRACE_ID_HEADER);
    if (traceId == null || !VALID.matcher(traceId).matches()) {
      traceId = UUID.randomUUID().toString().substring(0, 8);
    }
    MDC.put(MDC_KEY, traceId);
    response.setHeader(TRACE_ID_HEADER, traceId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_KEY);
    }
  }
}
