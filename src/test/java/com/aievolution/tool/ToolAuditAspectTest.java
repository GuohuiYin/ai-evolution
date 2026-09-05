package com.aievolution.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class ToolAuditAspectTest {

  private ToolAuditAspect aspect;
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void setUp() {
    aspect = new ToolAuditAspect();
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
}
