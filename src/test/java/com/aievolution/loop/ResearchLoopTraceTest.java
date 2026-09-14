package com.aievolution.loop;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * W11 #2：轨迹可观测。日志可还原完整研究轨迹（research-trace 专用 logger，同 tool-audit 先例； traceId 由 MDC pattern
 * 自动带出）；监听器端口为 SSE 轨迹推送（#3 接线）预留观测点。
 */
class ResearchLoopTraceTest {

  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void attachAppender() {
    Logger logger = (Logger) LoggerFactory.getLogger("research-trace");
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  private List<String> logged() {
    return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
  }

  @Test
  void logsEachStepAndExitSoTrajectoryIsReplayable() {
    ResearchLoop loop =
        new ResearchLoop(
            (q, history) ->
                history.isEmpty()
                    ? new ModelTurn.Act("先查行情", "getQuote", "600519")
                    : new ModelTurn.Final("够了", "收盘价 1700"),
            (tool, input) -> "1700.00",
            5);

    loop.run("茅台现在多少钱？");

    List<String> logs = logged();
    // 每步一条：步序 + 思考 + 动作 + 观察，四元组缺一不可（否则轨迹不可还原）
    assertThat(logs)
        .anySatisfy(
            line ->
                assertThat(line)
                    .contains("step=0")
                    .contains("thought=")
                    .contains("先查行情")
                    .contains("tool=getQuote")
                    .contains("observation=")
                    .contains("1700.00"));
    // 退出一条：原因 + 总步数
    assertThat(logs)
        .anySatisfy(
            line ->
                assertThat(line)
                    .contains("exit")
                    .contains("stopReason=FINAL_ANSWER")
                    .contains("steps=1"));
  }

  @Test
  void logsMaxStepsExit() {
    ResearchLoop loop =
        new ResearchLoop(
            (q, history) -> new ModelTurn.Act("继续查", "getQuote", "600519"),
            (tool, input) -> "obs",
            2);

    loop.run("追不完的问题");

    assertThat(logged())
        .anySatisfy(
            line ->
                assertThat(line)
                    .contains("exit")
                    .contains("stopReason=MAX_STEPS")
                    .contains("steps=2"));
  }

  @Test
  void notifiesListenerPerStepThenOnComplete() {
    List<Object> events = new ArrayList<>();
    LoopListener listener =
        new LoopListener() {
          @Override
          public void onStep(LoopStep step) {
            events.add(step);
          }

          @Override
          public void onComplete(LoopResult result) {
            events.add(result);
          }
        };
    ResearchLoop loop =
        new ResearchLoop(
            (q, history) ->
                history.isEmpty()
                    ? new ModelTurn.Act("想一下", "getQuote", "600519")
                    : new ModelTurn.Final("总结", "答案"),
            (tool, input) -> "obs",
            5,
            listener);

    LoopResult result = loop.run("问题");

    assertThat(events).hasSize(2);
    assertThat(events.get(0)).isEqualTo(result.steps().get(0));
    assertThat(events.get(1)).isSameAs(result);
  }

  @Test
  void threeArgConstructorRunsWithoutListener() {
    ResearchLoop loop =
        new ResearchLoop((q, h) -> new ModelTurn.Final("想好了", "答案"), (t, i) -> "obs", 5);
    assertThat(loop.run("问题").stopReason()).isEqualTo(StopReason.FINAL_ANSWER);
  }
}
