package com.aievolution.infra;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 业务 traceId → OTel span 属性桥（W25 #1，ADR-0020 第 4 条）：把 {@link TraceIdFilter} 的业务号挂到当前请求 span 的
 * {@code app.trace_id} 属性上——日志面按号 grep、链路面按属性 检索，两面同键互查。
 *
 * <p>两个落地选择：
 *
 * <ul>
 *   <li>落在 MVC 拦截器而非 Filter：拦截器执行时 ServerHttpObservation 的 span 已在当前线程 打开（observation filter
 *       包裹整条下游链），{@link Tracer#currentSpan()} 稳定可得； Filter 层则受注册顺序影响，可能早于 span 创建
 *   <li>读请求属性而非 MDC：业务号由 {@link TraceIdFilter#REQUEST_ATTR} 承载，与链路键位演进解耦（OTel 关联的 MDC 键已改名隔离，见
 *       {@link ModelObservabilityConfig#otelSlf4JEventListener}）
 * </ul>
 */
@Component
public class TraceIdSpanTagger implements HandlerInterceptor, WebMvcConfigurer {

  /** span 属性键：OTel GenAI 语义约定未覆盖业务追踪号，用 app.* 命名空间承载。 */
  static final String SPAN_ATTR = "app.trace_id";

  private final ObjectProvider<Tracer> tracer;

  /**
   * {@link ObjectProvider} 延迟取 {@link Tracer}：本类实现 WebMvcConfigurer 会被 WebMvc 切片测试一并装配，而切片里没有
   * Tracer——延迟取用使无 tracing 环境（切片测试、 tracing 关闭）下静默退化为无操作，而非上下文装配失败。
   */
  public TraceIdSpanTagger(ObjectProvider<Tracer> tracer) {
    this.tracer = tracer;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (!(request.getAttribute(TraceIdFilter.REQUEST_ATTR) instanceof String id)) {
      return true;
    }
    Tracer t = tracer.getIfAvailable();
    Span span = t == null ? null : t.currentSpan();
    if (span != null) {
      span.tag(SPAN_ATTR, id);
    }
    return true;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(this);
  }
}
