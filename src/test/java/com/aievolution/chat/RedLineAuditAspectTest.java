package com.aievolution.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.aievolution.compliance.RedLineGuard;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** 红线告警切面测试：命中必告警（WARN + 模式 + 摘录），干净回答零噪音。 */
class RedLineAuditAspectTest {

  private final RedLineAuditAspect aspect = new RedLineAuditAspect(new RedLineGuard());
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void attachAppender() {
    Logger logger = (Logger) LoggerFactory.getLogger(RedLineAuditAspect.class);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @Test
  void redLineHitLogsWarn() throws Throwable {
    ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
    when(pjp.proceed()).thenReturn(new ChatAnswer("综上分析，强烈建议立即全仓买入贵州茅台。", List.of()));

    Object result = aspect.scanReplyForRedLine(pjp);

    assertThat(result).isNotNull();
    assertThat(appender.list)
        .anySatisfy(
            e -> {
              assertThat(e.getLevel()).isEqualTo(Level.WARN);
              assertThat(e.getFormattedMessage())
                  .contains("stage=REDLINE_HIT")
                  .contains("patterns=");
            });
  }

  @Test
  void cleanReplyStaysSilent() throws Throwable {
    ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
    when(pjp.proceed()).thenReturn(new ChatAnswer("我不能提供买卖建议。600519 营收 1741.44 亿元。", List.of()));

    aspect.scanReplyForRedLine(pjp);

    assertThat(appender.list).isEmpty();
  }
}
