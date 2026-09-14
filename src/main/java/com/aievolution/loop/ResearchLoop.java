package com.aievolution.loop;

import java.util.ArrayList;
import java.util.List;
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
public class ResearchLoop {

  private static final Logger traceLog = LoggerFactory.getLogger("research-trace");
  private static final int SUMMARY_MAX = 100;

  private final ResearchModel model;
  private final ToolExecutor toolExecutor;
  private final int maxSteps;
  private final LoopListener listener;

  public ResearchLoop(ResearchModel model, ToolExecutor toolExecutor, int maxSteps) {
    this(model, toolExecutor, maxSteps, LoopListener.NONE);
  }

  public ResearchLoop(
      ResearchModel model, ToolExecutor toolExecutor, int maxSteps, LoopListener listener) {
    if (maxSteps < 1) {
      // 上限必须为正，否则循环形同虚设——配置错误 fail-fast（A11：该值是策略，接线处走配置）
      throw new IllegalArgumentException("maxSteps 必须 ≥ 1: " + maxSteps);
    }
    this.model = model;
    this.toolExecutor = toolExecutor;
    this.maxSteps = maxSteps;
    this.listener = listener;
  }

  public LoopResult run(String question) {
    List<LoopStep> steps = new ArrayList<>();
    while (steps.size() < maxSteps) {
      ModelTurn turn = model.nextTurn(question, List.copyOf(steps));
      if (turn instanceof ModelTurn.Final f) {
        return finish(new LoopResult(f.answer(), List.copyOf(steps), StopReason.FINAL_ANSWER));
      }
      steps.add(executeStep(steps.size(), (ModelTurn.Act) turn));
    }
    return finish(new LoopResult(null, List.copyOf(steps), StopReason.MAX_STEPS));
  }

  /** 执行一步并留痕：审计计时用 nanoTime（tool-audit 先例，单调时钟防系统回拨） */
  private LoopStep executeStep(int index, ModelTurn.Act act) {
    long startNanos = System.nanoTime();
    String observation = toolExecutor.execute(act.tool(), act.input());
    LoopStep step = new LoopStep(index, act.thought(), act.tool(), act.input(), observation);
    traceLog.info(
        "step={} thought={} tool={} input={} observation={} elapsedMs={}",
        index,
        summarize(act.thought()),
        act.tool(),
        summarize(act.input()),
        summarize(observation),
        (System.nanoTime() - startNanos) / 1_000_000);
    listener.onStep(step);
    return step;
  }

  private LoopResult finish(LoopResult result) {
    traceLog.info("exit stopReason={} steps={}", result.stopReason(), result.steps().size());
    listener.onComplete(result);
    return result;
  }

  // 日志摘要策略：空白归一 + 截断。与 ToolAuditAspect 同款（第二处副本）——
  // 第三处出现时立即收口到 infra（规则三是最后期限，不是许可证）
  private static String summarize(String text) {
    if (text == null) {
      return "";
    }
    String normalized = text.replaceAll("\\s+", " ");
    return normalized.length() <= SUMMARY_MAX
        ? normalized
        : normalized.substring(0, SUMMARY_MAX) + "…";
  }
}
