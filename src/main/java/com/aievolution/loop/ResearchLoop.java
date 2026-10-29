package com.aievolution.loop;

import com.aievolution.infra.LogSummaries;
import com.aievolution.infra.TraceSpans;
import io.micrometer.tracing.Span;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 显式 ReAct 研究循环（W11 #1）：把"模型决定 → 调工具 → 再决定"从 Spring AI 内部循环搬到明面上，
 * 轨迹可见、步数可控、中途状态可观测——Harness「Correct」域的系统化起点。
 *
 * <p>串行骨架：每步至多一次工具调用；并行编排是 W14+ 进阶（周计划"明确不做"）。 上限思想复用 {@code
 * spring.ai.tools.limits}：超限优雅退出，不击穿、不编造。
 *
 * <p>轨迹可观测（W11 #2）：每步与退出写 {@code research-trace} 专用 logger（tool-audit 先例； traceId 由控制台 pattern 经
 * MDC 自动带出），同时回调 {@link LoopListener}—— SSE 轨迹推送与轨迹评估指标共用这一观测点。
 */
public class ResearchLoop implements ResearchLoopEngine {

  private static final Logger traceLog = LoggerFactory.getLogger("research-trace");

  private final ResearchModel model;
  private final ToolExecutor toolExecutor;
  private final int maxSteps;
  private final LoopListener listener;
  // 可空：测试/老装配位缺席时直执——埋点永不改变业务语义（TraceSpans 同款约定）
  private final TraceSpans traceSpans;

  public ResearchLoop(ResearchModel model, ToolExecutor toolExecutor, int maxSteps) {
    this(model, toolExecutor, maxSteps, LoopListener.NONE);
  }

  public ResearchLoop(
      ResearchModel model, ToolExecutor toolExecutor, int maxSteps, LoopListener listener) {
    this(model, toolExecutor, maxSteps, listener, null);
  }

  public ResearchLoop(
      ResearchModel model,
      ToolExecutor toolExecutor,
      int maxSteps,
      LoopListener listener,
      TraceSpans traceSpans) {
    if (maxSteps < 1) {
      // 上限必须为正，否则循环形同虚设——配置错误 fail-fast（A11：该值是策略，接线处走配置）
      throw new IllegalArgumentException("maxSteps 必须 ≥ 1: " + maxSteps);
    }
    this.model = model;
    this.toolExecutor = toolExecutor;
    this.maxSteps = maxSteps;
    this.listener = listener;
    this.traceSpans = traceSpans;
  }

  public LoopResult run(String question) {
    List<LoopStep> steps = new ArrayList<>();
    while (steps.size() < maxSteps) {
      int index = steps.size();
      // 一轮 ReAct 步 = 模型决策 + 至多一次工具执行：收口为一个 react.step 父段（W25 #1a），
      // 内层 chat_client/tool.* 自动段挂在其下，面板可按 react.step.index 逐轮过滤
      ModelTurn turn =
          tracedStep(
              index,
              () -> {
                ModelTurn t = model.nextTurn(question, List.copyOf(steps));
                if (t instanceof ModelTurn.Act act) {
                  steps.add(executeStep(index, act));
                }
                return t;
              },
              (span, t) -> {
                span.tag("react.step.decision", t instanceof ModelTurn.Final ? "final" : "act");
                if (t instanceof ModelTurn.Act act) {
                  span.tag("gen_ai.tool.name", act.tool());
                }
              });
      if (turn instanceof ModelTurn.Final f) {
        return finish(new LoopResult(f.answer(), List.copyOf(steps), StopReason.FINAL_ANSWER));
      }
    }
    return finish(synthesizeAtMaxSteps(question, steps));
  }

  /** react.step 父段：TraceSpans 缺席直执；受检异常永不发生（body 不抛），原语义透传 */
  private <T> T tracedStep(int index, Supplier<T> body, BiConsumer<Span, T> onSuccess) {
    if (traceSpans == null) {
      return body.get();
    }
    try {
      return traceSpans.traced(
          "react.step",
          Map.of("openinference.span.kind", "CHAIN", "react.step.index", String.valueOf(index)),
          body::get,
          onSuccess);
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /**
   * 触顶收尾（W20 #1 裁决①B）：追加无工具合成步——已查证 observations 喂回模型强制出终答； 合成失败则 answer=null，兜底措辞交调用方（诚实红线：不硬凑答案）。
   */
  private LoopResult synthesizeAtMaxSteps(String question, List<LoopStep> steps) {
    java.util.Optional<String> synthesized = model.synthesize(question, List.copyOf(steps));
    if (synthesized.isPresent()) {
      traceLog.info("synthesize outcome=success steps={}", steps.size());
    } else {
      traceLog.warn("synthesize outcome=fallback steps={}", steps.size());
    }
    return new LoopResult(synthesized.orElse(null), List.copyOf(steps), StopReason.MAX_STEPS);
  }

  /** 执行一步并留痕：审计计时用 nanoTime（tool-audit 先例，单调时钟防系统回拨） */
  private LoopStep executeStep(int index, ModelTurn.Act act) {
    long startNanos = System.nanoTime();
    String observation = toolExecutor.execute(act.tool(), act.input());
    LoopStep step = new LoopStep(index, act.thought(), act.tool(), act.input(), observation);
    traceLog.info(
        "step={} thought={} tool={} input={} observation={} elapsedMs={}",
        index,
        LogSummaries.summarize(act.thought()),
        act.tool(),
        LogSummaries.summarize(act.input()),
        LogSummaries.summarize(observation),
        (System.nanoTime() - startNanos) / 1_000_000);
    listener.onStep(step);
    return step;
  }

  private LoopResult finish(LoopResult result) {
    traceLog.info("exit stopReason={} steps={}", result.stopReason(), result.steps().size());
    listener.onComplete(result);
    return result;
  }
}
