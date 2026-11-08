package com.aievolution.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 频率限制过滤器（W12 #3）：固定窗口，每身份每分钟 N 次（配置化，A11）。
 *
 * <p>设计决策：
 *
 * <ul>
 *   <li>身份 = Bearer JWT 的 {@code sub} 声明（W30 #2）；缺失时退化客户端 IP——排在鉴权<b>之前</b>，
 *       撞库/无凭证试探同样吃额度（鉴权失败不是免费通道）
 *   <li>公开面（{@link SecurityAllowlist}）不限流：健康探针与文档页不应占用业务额度
 *   <li>超限 429 + ProblemDetail（B3）+ {@code Retry-After}（距下一窗口秒数）—— 客户端可自我节流，而不是盲目重试
 *   <li>单实例内存实现（明确不做分布式限流，周计划既定）；窗口按分钟对齐自然时刻， 突刺语义直白可解释（边界分钟最多 2N 的固定窗口固有特性，接受并记录）
 *   <li>时钟构造器注入：窗口翻转行为由单测锁定，不靠 sleep 碰运气
 * </ul>
 */
public class RateLimitFilter extends OncePerRequestFilter {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final long WINDOW_SECONDS = 60;
  private static final String BEARER_PREFIX = "Bearer ";

  /** 单窗口计数器：count 原子自增；窗口过期整体替换（compute 内原子完成，无锁外竞态） */
  private static final class Window {
    private final long windowStart;
    private final AtomicInteger count;

    private Window(long windowStart, AtomicInteger count) {
      this.windowStart = windowStart;
      this.count = count;
    }
  }

  private final int maxPerMinute;
  private final LongSupplier epochSeconds;
  private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

  public RateLimitFilter(int maxPerMinute, LongSupplier epochSeconds) {
    if (maxPerMinute < 1) {
      // 配置错误 fail-fast（A11）：限流为 0 等于拒绝服务，宁可启动失败
      throw new IllegalStateException("ai.security.rate-limit.per-minute 必须 ≥ 1: " + maxPerMinute);
    }
    this.maxPerMinute = maxPerMinute;
    this.epochSeconds = epochSeconds;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (SecurityAllowlist.isPublic(request.getRequestURI())) {
      filterChain.doFilter(request, response);
      return;
    }
    long now = epochSeconds.getAsLong();
    long windowStart = now - (now % WINDOW_SECONDS);
    String identity = identityOf(request);

    Window window =
        windows.compute(
            identity,
            (key, old) ->
                (old == null || old.windowStart != windowStart)
                    ? new Window(windowStart, new AtomicInteger(1))
                    : increment(old));
    if (window.count.get() > maxPerMinute) {
      // 安全事件审计流（W31 #4）：限流命中留痕（identity=分桶键）
      SecurityAudit.rateLimited(identity, request.getRequestURI());
      writeTooManyRequests(response, WINDOW_SECONDS - (now % WINDOW_SECONDS));
      return;
    }
    filterChain.doFilter(request, response);
  }

  private static Window increment(Window window) {
    window.count.incrementAndGet();
    return window;
  }

  /**
   * 限流身份（W30 #2）：Bearer JWT 按 {@code sub} 声明分桶；无凭证按来源 IP。
   *
   * <p>此处只摘 payload 不验签——分桶不是安全决策（验签在 Spring Security 资源服务器链完成）， 伪造 sub 最坏后果是替别人吃额度，不放大自身额度；解析失败的非法
   * token 按原值整串分桶， 随后由资源服务器链统一 401。token 不落日志防泄漏。
   */
  private static String identityOf(HttpServletRequest request) {
    String authorization = request.getHeader("Authorization");
    if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
      return "sub:" + jwtSubjectOrRaw(authorization.substring(BEARER_PREFIX.length()));
    }
    return "ip:" + request.getRemoteAddr();
  }

  /** 摘 JWT payload 的 sub；非三段结构或解码/解析失败时退回原值（整串作桶键，语义与旧 key 分桶一致） */
  private static String jwtSubjectOrRaw(String token) {
    String[] parts = token.split("\\.");
    if (parts.length != 3) {
      return token;
    }
    try {
      byte[] payload = java.util.Base64.getUrlDecoder().decode(parts[1]);
      String sub = OBJECT_MAPPER.readTree(payload).path("sub").asText(null);
      return (sub != null && !sub.isBlank()) ? sub : token;
    } catch (RuntimeException | java.io.IOException e) {
      return token;
    }
  }

  private void writeTooManyRequests(HttpServletResponse response, long retryAfterSeconds)
      throws IOException {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.TOO_MANY_REQUESTS, "请求频率超限：每身份每分钟 %d 次".formatted(maxPerMinute));
    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    OBJECT_MAPPER.writeValue(response.getWriter(), problem);
  }
}
