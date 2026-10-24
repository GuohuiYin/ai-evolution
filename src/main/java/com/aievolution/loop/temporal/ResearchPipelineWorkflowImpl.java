package com.aievolution.loop.temporal;

import com.aievolution.loop.LoopResult;
import com.aievolution.loop.LoopStep;
import com.aievolution.loop.StopReason;
import com.aievolution.loop.pipeline.Plan;
import com.aievolution.loop.pipeline.ReviewVerdict;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;

/**
 * 三角色管线实现（W22 #1c）：{@code plan → build → review} 串行，打回批注回流 Builder 重写， 打回耗尽（首轮 + 重打 2
 * 次仍不过）出拒答文案——宁可不答不可错答（设计 §1.3）。
 *
 * <p>确定性纪律同 {@link ResearchLoopWorkflowImpl}：只用入参/activity 返回值/{@link Workflow} API。
 *
 * <p>重试：plan/review 单层重试（attempts=2，供应商瞬态故障由 FailoverChatModel 在 activity 内部兜底）； build
 * 含多轮模型+工具调用，超时放宽至 10 分钟。
 *
 * <p>HITL 预埋（W22 #1f）：{@link #shouldHoldForApproval} 是 Reviewer 通过后、
 * 终答发出前的断点评估位——触发策略三条（红线擦边/低置信证据/打回耗尽升级人工） 在案，本周恒 false 直透，W23 接通 Signal 挂起。
 */
public class ResearchPipelineWorkflowImpl implements ResearchPipelineWorkflow {

  private static final Logger traceLog = Workflow.getLogger("research-trace");

  /** 打回上限（设计 §1.3：打回 ≤2 次） */
  private static final int MAX_REJECTIONS = 2;

  /** 打回耗尽的拒答文案（宁可不答不可错答） */
  static final String REFUSAL = "经多轮独立复核仍无法满足溯源与合规要求，本次不予作答。可缩小问题范围或澄清口径后重试。";

  private final List<LoopStep> steps = new ArrayList<>();
  private String status = "not-started";
  private String approvalComment;
  private boolean heldForApproval;

  private final ResearchPipelineActivities quickActivities =
      Workflow.newActivityStub(
          ResearchPipelineActivities.class,
          ActivityOptions.newBuilder()
              .setStartToCloseTimeout(Duration.ofSeconds(120))
              .setRetryOptions(
                  RetryOptions.newBuilder()
                      .setInitialInterval(Duration.ofSeconds(1))
                      .setMaximumAttempts(2)
                      .build())
              .build());

  private final ResearchPipelineActivities buildActivities =
      Workflow.newActivityStub(
          ResearchPipelineActivities.class,
          ActivityOptions.newBuilder()
              // build 内含逐子问题 ReAct 循环 + 起草合成，模型调用多次——超时放宽
              .setStartToCloseTimeout(Duration.ofSeconds(600))
              .setRetryOptions(
                  RetryOptions.newBuilder()
                      .setInitialInterval(Duration.ofSeconds(1))
                      .setMaximumAttempts(2)
                      .build())
              .build());

  @Override
  public LoopResult runResearch(ResearchRunInput input) {
    this.status = "running";
    Plan plan = this.quickActivities.plan(input.question(), input.history(), input.traceId());
    traceLog.info("plan ready subQuestions={}", plan.subQuestions().size());

    String comment = "";
    for (int attempt = 0; attempt <= MAX_REJECTIONS; attempt++) {
      BuildOutcome outcome =
          this.buildActivities.build(
              input.question(), plan, input.history(), comment, input.maxSteps(), input.traceId());
      this.steps.clear();
      this.steps.addAll(outcome.steps());
      if (outcome.draft() == null) {
        // 合成失败：诚实红线（同单循环 MAX_STEPS 语义）——answer=null，兜底措辞交调用方
        traceLog.warn("build draft=null steps={}", this.steps.size());
        return finish(new LoopResult(null, List.copyOf(this.steps), StopReason.MAX_STEPS));
      }
      this.status = "reviewing";
      ReviewVerdict verdict =
          this.quickActivities.review(input.question(), outcome, input.traceId());
      if (verdict.isPass()) {
        if (shouldHoldForApproval(input, outcome)) {
          // W23 接通：Workflow.await(() -> this.approvalComment != null)
          this.status = "awaiting-approval";
        }
        return finish(
            new LoopResult(outcome.draft(), List.copyOf(this.steps), StopReason.FINAL_ANSWER));
      }
      traceLog.info("review rejected attempt={} comment={}", attempt, verdict.comment());
      comment = verdict.comment();
      this.status = "running";
    }
    // 打回耗尽 → 拒答（升级人工是 HITL 触发策略之三，W23 接通）
    traceLog.warn("review rejections exhausted, refuse to answer");
    return finish(new LoopResult(REFUSAL, List.copyOf(this.steps), StopReason.REVIEW_REJECTED));
  }

  /**
   * HITL 断点评估（W22 #1f 预埋，本周恒 false 直透）。 W23 触发策略三条：①红线擦边（Reviewer 通过但批注非空）；
   * ②低置信证据（证据包为空或单源）；③打回耗尽升级人工。
   */
  private boolean shouldHoldForApproval(ResearchRunInput input, BuildOutcome outcome) {
    return false;
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

  @Override
  public void approve(String comment) {
    this.approvalComment = comment;
    this.heldForApproval = false;
  }

  @Override
  public boolean pendingApproval() {
    return this.heldForApproval;
  }
}
