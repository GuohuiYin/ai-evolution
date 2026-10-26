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
 * <p>HITL（W23 #1 接通，裁决①B）：打回耗尽时挂起等人工 Signal（{@link #awaitHumanDecision}）——
 * 唯一启用触发；红线擦边/低置信证据两条策略留待后续裁决。挂起/决定/超时经 hitlEvent activity 留痕 （SSE 轨迹可见断点），等待用 {@link
 * Workflow#await} 确定性 API。
 */
public class ResearchPipelineWorkflowImpl implements ResearchPipelineWorkflow {

  private static final Logger traceLog = Workflow.getLogger("research-trace");

  /** 打回上限（设计 §1.3：打回 ≤2 次） */
  private static final int MAX_REJECTIONS = 2;

  /** 打回耗尽的拒答文案（宁可不答不可错答） */
  static final String REFUSAL = "经多轮独立复核仍无法满足溯源与合规要求，本次不予作答。可缩小问题范围或澄清口径后重试。";

  /** 人工放行标注（W23 #1）：自动复核未通过、审批人背书放出——如实标注是红线文化 */
  static final String HUMAN_OVERRIDE_NOTE = "\n\n（说明：本回答自动复核未通过，经人工审批放行，内容由审批人背书。）";

  private final List<LoopStep> steps = new ArrayList<>();
  private String status = "not-started";
  private String hitlDecision;
  private String hitlComment;
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
    BuildOutcome lastOutcome = null;
    for (int attempt = 0; attempt <= MAX_REJECTIONS; attempt++) {
      BuildOutcome outcome =
          this.buildActivities.build(
              input.question(), plan, input.history(), comment, input.maxSteps(), input.traceId());
      lastOutcome = outcome;
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
        return finish(
            new LoopResult(outcome.draft(), List.copyOf(this.steps), StopReason.FINAL_ANSWER));
      }
      traceLog.info("review rejected attempt={} comment={}", attempt, verdict.comment());
      comment = verdict.comment();
      this.status = "running";
    }
    // 打回耗尽：W23 #1（裁决①B）升级人工——挂起等放行/驳回；开关关闭时沿用即时拒答
    if (input.hitlEnabled() && lastOutcome != null) {
      return awaitHumanDecision(input, lastOutcome);
    }
    traceLog.warn("review rejections exhausted, refuse to answer");
    return finish(new LoopResult(REFUSAL, List.copyOf(this.steps), StopReason.REVIEW_REJECTED));
  }

  /**
   * 打回耗尽挂起点（W23 #1）：等人工 Signal 至 {@code hitlAwaitSeconds} 上限。 放行=放出最后草稿并如实标注人工背书；驳回/超时=fail-closed
   * 拒答。 挂起原因/人工决定/等待耗时经 hitlEvent activity 入 SSE 轨迹与研究日志（留痕纪律）。
   */
  private LoopResult awaitHumanDecision(ResearchRunInput input, BuildOutcome lastOutcome) {
    this.status = "awaiting-approval";
    this.heldForApproval = true;
    long holdStart = Workflow.currentTimeMillis();
    this.quickActivities.hitlEvent(
        "hold", "打回耗尽升级人工：复核 " + (MAX_REJECTIONS + 1) + " 轮未过，挂起等人工放行/驳回", input.traceId());
    traceLog.warn("hitl hold reason=review-rejections-exhausted, awaiting human decision");
    boolean decided =
        Workflow.await(
            Duration.ofSeconds(input.hitlAwaitSeconds()), () -> this.hitlDecision != null);
    this.heldForApproval = false;
    long waitedMs = Workflow.currentTimeMillis() - holdStart;
    if (!decided) {
      // 缺席策略显式化：超时未决 fail-closed 拒答（宁可不答不可错答）
      this.quickActivities.hitlEvent(
          "timeout", "审批超时未决（等待 " + waitedMs + "ms），按缺席策略拒答", input.traceId());
      traceLog.warn("hitl await timeout after {}ms, fail-closed refusal", waitedMs);
      return finish(new LoopResult(REFUSAL, List.copyOf(this.steps), StopReason.REVIEW_REJECTED));
    }
    this.quickActivities.hitlEvent(
        this.hitlDecision,
        (this.hitlComment == null ? "" : this.hitlComment) + "（等待 " + waitedMs + "ms）",
        input.traceId());
    if ("approve".equals(this.hitlDecision)) {
      traceLog.info("hitl approved comment={} waited={}ms", this.hitlComment, waitedMs);
      return finish(
          new LoopResult(
              lastOutcome.draft() + HUMAN_OVERRIDE_NOTE,
              List.copyOf(this.steps),
              StopReason.FINAL_ANSWER));
    }
    traceLog.warn("hitl rejected comment={} waited={}ms", this.hitlComment, waitedMs);
    return finish(new LoopResult(REFUSAL, List.copyOf(this.steps), StopReason.REVIEW_REJECTED));
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
    this.hitlDecision = "approve";
    this.hitlComment = comment;
  }

  @Override
  public void reject(String comment) {
    this.hitlDecision = "reject";
    this.hitlComment = comment;
  }

  @Override
  public boolean pendingApproval() {
    return this.heldForApproval;
  }
}
