package com.aievolution.infra;

import java.util.List;
import org.springframework.util.AntPathMatcher;

/**
 * 安全放行清单单点（W12 #3a，A11 两次即收口）：鉴权过滤器与限流过滤器共用同一份 "公开面"定义——两处各自持有就是两份会漂移的安全策略，必须共享。
 *
 * <p>每一项都是一条显式决策（W12 #2 定），新增放行路径必须改这里并由两个过滤器的测试同步锁定：
 *
 * <ul>
 *   <li>{@code /}、{@code /index.html}、{@code /favicon.ico}：对话页静态资源（页面放行，API 持证）
 *   <li>{@code /swagger-ui/**}、{@code /v3/api-docs*}：本地 API 文档（A7；部署侧经 ConfigMap 关闭 UI）
 *   <li>{@code /actuator/health/**}：K8s 健康探针（kubelet 不带凭证）；metrics/info 不在其列
 * </ul>
 */
public final class SecurityAllowlist {

  private static final List<String> PUBLIC_PATHS =
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

  private SecurityAllowlist() {}

  /** 该路径是否属公开面（免鉴权、免限流） */
  public static boolean isPublic(String path) {
    return PUBLIC_PATHS.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
  }
}
