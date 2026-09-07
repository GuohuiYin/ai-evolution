package com.aievolution.chat;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** 路由决策日志测试：路由是"系统的决策"，决策必须可见—— 日志断言即文档（route/reason 两个键是排查"为什么走这条路"的唯一现场）。 */
class ChatRouterLoggingTest {

  private final ChatRouter router = new ChatRouter(List.of("营收"));
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void attachAppender() {
    Logger logger = (Logger) LoggerFactory.getLogger(ChatRouter.class);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  private String lastLog() {
    List<ILoggingEvent> events = appender.list;
    assertThat(events).as("路由决策必须留日志").isNotEmpty();
    ILoggingEvent last = events.getLast();
    assertThat(last.getLevel()).isEqualTo(Level.INFO);
    return last.getFormattedMessage();
  }

  @Test
  void stockCodeRouteLogsReason() {
    router.route("600519 最近怎么样");
    assertThat(lastLog()).contains("route=AGENT").contains("reason=stock_code");
  }

  @Test
  void keywordRouteLogsMatchedKeyword() {
    router.route("去年营收怎么样");
    assertThat(lastLog()).contains("route=AGENT").contains("reason=keyword:营收");
  }

  @Test
  void fallbackRouteLogged() {
    router.route("酿造工艺有什么特点");
    assertThat(lastLog()).contains("route=RAG").contains("reason=fallback");
  }
}
