package com.aievolution.loop.temporal;

import com.aievolution.loop.LoopResult;
import com.aievolution.loop.LoopStep;
import com.aievolution.loop.StopReason;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;

/**
 * {@link ResearchLoopWorkflow} 实现：逐字对齐 {@code ResearchLoop} 的串行语义 （模型决策 → FINAL 收敛 / ACT 执行 →
 * 触顶合成收尾），差异只在状态驻留位置—— workflow 内存（事件历史 checkpoint）而非进程堆。
 *
 * <p>确定性纪律：本类只用 workflow 入参/activity 返回值/{@link Workflow} API—— 禁 System 时间、线程、随机数、直连 IO（重放会分叉）。日志走
 * {@link Workflow#getLogger} （重放时自动抑制重复输出）。
 *
 * <p>重试策略：模型/工具调用单层重试（maximumAttempts=2）——供应商瞬态故障已由 FailoverChatModel（W12 #4）在 activity
 * 内部兜底，activity 重试只兜进程级瞬断， 不与步数预算叠加失控（步数预算仍是 maxSteps 硬顶）。
 */
public class ResearchLoopWorkflowImpl implements ResearchLoopWorkflow {

  private static final Logger traceLog = Workflow.getLogger("research-trace");

  private final List<LoopStep> steps = new ArrayList<>();
  private String status = "not-started";

  private final ResearchLoopActivities activities =
      Workflow.newActivityStub(
          ResearchLoopActivities.class,
          ActivityOptions.newBuilder()
              // 模型调用含供应商 failover，单次可能数十秒；留足裕量
              .setStartToCloseTimeout(Duration.ofSeconds(120))
              .setRetryOptions(
                  RetryOptions.newBuilder()
                      .setInitialInterval(Duration.ofSeconds(1))
                      .setMaximumAttempts(2)
                      .build())
              .build());

  @Override
  public LoopResult runResearch(ResearchRunInput input) {
    this.status = "running";
    while (this.steps.size() < input.maxSteps()) {
      ModelTurnPayload turn =
          this.activities.nextTurn(
              input.question(), List.copyOf(this.steps), input.history(), input.traceId());
      if (turn.isFinal()) {
        return finish(
            new LoopResult(turn.answer(), List.copyOf(this.steps), StopReason.FINAL_ANSWER));
      }
      this.steps.add(
          this.activities.executeStep(
              this.steps.size(), turn.thought(), turn.tool(), turn.input(), input.traceId()));
    }
    this.status = "synthesizing";
    String synthesized =
        this.activities.synthesize(
            input.question(), List.copyOf(this.steps), input.history(), input.traceId());
    if (synthesized.isEmpty()) {
      traceLog.warn("synthesize outcome=fallback steps={}", this.steps.size());
    } else {
      traceLog.info("synthesize outcome=success steps={}", this.steps.size());
    }
    // 空串 = 合成失败 → answer=null，兜底措辞交调用方（诚实红线，同 inline 引擎语义）
    return finish(
        new LoopResult(
            synthesized.isEmpty() ? null : synthesized,
            List.copyOf(this.steps),
            StopReason.MAX_STEPS));
  }

  private LoopResult finish(LoopResult result) {
    traceLog.info("exit stopReason={} steps={}", result.stopReason(), result.steps().size());
    this.status = "completed";
    return result;
  }

  @Override
  public List<LoopStep> currentSteps() {
    return List.copyOf(this.steps);
  }

  @Override
  public String status() {
    return this.status;
  }
}
