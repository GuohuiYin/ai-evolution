package com.aievolution.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.aievolution.infra.TraceSpans;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

class ToolAuditAspectTest {

  private ToolAuditAspect aspect;
  private ListAppender<ILoggingEvent> appender;
  private Tracer tracer;
  private Span span;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    // 默认关掉 tracing（Tracer 缺席语义）：既有审计断言与埋点解耦，各自独立成立
    ObjectProvider<Tracer> provider = mock(ObjectProvider.class);
    aspect = new ToolAuditAspect(new TraceSpans(provider));
    tracer = mock(Tracer.class);
    span = mock(Span.class);
    Logger logger = (Logger) LoggerFactory.getLogger("tool-audit");
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  private ProceedingJoinPoint joinPoint(String method, Object[] args, Object result)
      throws Throwable {
    ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
    Signature signature = mock(Signature.class);
    when(signature.getName()).thenReturn(method);
    when(pjp.getSignature()).thenReturn(signature);
    when(pjp.getArgs()).thenReturn(args);
    when(pjp.proceed()).thenReturn(result);
    return pjp;
  }

  @Test
  void successfulToolCallIsAudited() throws Throwable {
    Object result =
        aspect.around(
            joinPoint("getFinancialSummary", new Object[] {"600519", 2024}, "营收1741.44亿元"));

    assertThat(result).isEqualTo("营收1741.44亿元");
    // 金融合规刚需：工具名 + 调用参数 + 结果摘要 + 耗时，全留痕
    assertThat(appender.list)
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.getFormattedMessage())
                  .contains("getFinancialSummary")
                  .contains("600519")
                  .contains("2024")
                  .contains("success")
                  .contains("elapsedMs=");
            });
  }

  @Test
  void failedToolCallIsAuditedAndRethrown() throws Throwable {
    ProceedingJoinPoint pjp = joinPoint("getDailyQuotes", new Object[] {"600519"}, null);
    when(pjp.proceed()).thenThrow(new IllegalStateException("数据源超时"));

    assertThatThrownBy(() -> aspect.around(pjp)).isInstanceOf(IllegalStateException.class);
    assertThat(appender.list)
        .singleElement()
        .satisfies(
            e ->
                assertThat(e.getFormattedMessage())
                    .contains("getDailyQuotes")
                    .contains("error")
                    .contains("IllegalStateException"));
  }

  @Test
  void toolCallProducesExecuteToolSpan() throws Throwable {
    ObjectProvider<Tracer> provider = tracingProvider();
    aspect = new ToolAuditAspect(new TraceSpans(provider));

    aspect.around(joinPoint("getFinancialSummary", new Object[] {"600519", 2024}, "营收1741.44亿元"));

    verify(span).tag("gen_ai.operation.name", "execute_tool");
    verify(span).tag("gen_ai.tool.name", "getFinancialSummary");
    verify(span).tag("openinference.span.kind", "TOOL");
    verify(span).tag("input.value", "600519,2024");
    verify(span).tag("output.value", "营收1741.44亿元");
    verify(span).end();
  }

  @Test
  void failedToolCallSpanKeepsIdentityAttributes() throws Throwable {
    ObjectProvider<Tracer> provider = tracingProvider();
    aspect = new ToolAuditAspect(new TraceSpans(provider));
    ProceedingJoinPoint pjp = joinPoint("getDailyQuotes", new Object[] {"600519"}, null);
    IllegalStateException boom = new IllegalStateException("数据源超时");
    when(pjp.proceed()).thenThrow(boom);

    assertThatThrownBy(() -> aspect.around(pjp)).isSameAs(boom);

    // 失败路径身份属性在场（执行前打标），异常记 span error
    verify(span).tag("gen_ai.operation.name", "execute_tool");
    verify(span).tag("gen_ai.tool.name", "getDailyQuotes");
    verify(span).error(boom);
    verify(span).end();
  }

  @SuppressWarnings("unchecked")
  private ObjectProvider<Tracer> tracingProvider() {
    ObjectProvider<Tracer> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(tracer);
    when(tracer.nextSpan()).thenReturn(span);
    when(span.name(org.mockito.ArgumentMatchers.anyString())).thenReturn(span);
    when(span.start()).thenReturn(span);
    when(tracer.withSpan(span)).thenReturn(mock(Tracer.SpanInScope.class));
    return provider;
  }
}
