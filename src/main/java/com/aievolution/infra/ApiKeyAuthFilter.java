package com.aievolution.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * API Key 鉴权过滤器（W12 #2）：默认拒绝 + 显式放行清单——"全端点裸奔"硬伤的修复。
 *
 * <p>设计决策：
 *
 * <ul>
 *   <li>请求头 {@code X-API-Key}，比对走常量时间（{@link MessageDigest#isEqual}），防时序侧信道
 *   <li>放行清单只含无状态公开面：对话页静态资源、Swagger UI/api-docs（本地文档）、 actuator 健康探针（K8s kubelet
 *       不带凭证）；metrics/info 等其余端点一律要 key
 *   <li>401 返回 RFC 7807 ProblemDetail（约定 B3），与全局异常处理同格式
 *   <li>配置 key 为空即构造失败（fail-fast）：鉴权不存在"忘记开"的静默中间态
 *   <li>有意不标 @Component：只经 {@code FilterRegistrationBean} 注册，@WebMvcTest 切片不加载 （鉴权行为由专属单测 +
 *       集成测试锁定，不污染控制器切片）
 * </ul>
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

  public static final String API_KEY_HEADER = "X-API-Key";

  /** 放行清单（显式决策，测试锁定）：精确匹配 / 与 /index.html；前缀匹配文档与健康探针 */
  private static final List<String> ALLOWLIST =
      List.of(
          "/",
          "/index.html",
          "/favicon.ico",
          "/swagger-ui/**",
          "/v3/api-docs",
          "/v3/api-docs.yaml",
          "/v3/api-docs/**",
          "/actuator/health/**");

  private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final byte[] expectedKey;

  public ApiKeyAuthFilter(String apiKey) {
    if (apiKey == null || apiKey.isBlank()) {
      // 配置错误 fail-fast（A11）：宁可启动失败，不可带病裸奔
      throw new IllegalStateException(
          "ai.security.api-key 未配置：API Key 鉴权为强制项（W12 #2），请设置环境变量 AI_EVOLUTION_API_KEY");
    }
    this.expectedKey = apiKey.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String path = request.getRequestURI();
    boolean allowed = ALLOWLIST.stream().anyMatch(p -> PATH_MATCHER.match(p, path));
    if (allowed) {
      filterChain.doFilter(request, response);
      return;
    }
    String presented = request.getHeader(API_KEY_HEADER);
    if (presented != null
        && MessageDigest.isEqual(expectedKey, presented.getBytes(StandardCharsets.UTF_8))) {
      filterChain.doFilter(request, response);
      return;
    }
    writeUnauthorized(response);
  }

  /** 401 统一走 ProblemDetail（B3）：调用方拿到的错误格式与业务异常一致 */
  private static void writeUnauthorized(HttpServletResponse response) throws IOException {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.UNAUTHORIZED, "缺少或非法 API Key（请求头 " + API_KEY_HEADER + "）");
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    OBJECT_MAPPER.writeValue(response.getWriter(), problem);
  }
}
