package com.aievolution.loop.temporal;

import static org.assertj.core.api.Assertions.assertThat;

import com.aievolution.loop.LoopResult;
import com.aievolution.loop.LoopStep;
import com.aievolution.loop.StopReason;
import com.aievolution.loop.pipeline.Channel;
import com.aievolution.loop.pipeline.Plan;
import com.aievolution.loop.pipeline.ReviewVerdict;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 三角色管线 workflow 单测（W22 #1c 后半）：TestWorkflowEnvironment 进程内跑真 workflow， activity 用脚本化桩——对齐设计
 * §1.3/§1.4 的四条编排语义：一次通过 / 打回批注回流重写 / 打回耗尽拒答 / 合成失败诚实红线（answer=null 且不再复核）。
 */
class ResearchPipelineWorkflowTest {

  private static final String TASK_QUEUE = "research-pipeline-test";

  /** 脚本化三角色 activity 桩：plan 固定，build 计数并记录收到的批注，review 按队列出裁决 */
  static class ScriptedActivities implements ResearchPipelineActivities {
    Plan plan = new Plan(List.of(new Plan.SubQuestion("茅台 2024 营收", Channel.RAG)));
    final Deque<ReviewVerdict> verdicts = new ArrayDeque<>();
    final List<String> receivedComments = new ArrayList<>();
    String draft = "草稿答案";
    int planCalls = 0;
    int buildCalls = 0;
    int reviewCalls = 0;

    @Override
    public Plan plan(String question, List<HistoryTurn> history, String traceId) {
      this.planCalls++;
      return this.plan;
    }

    @Override
    public BuildOutcome build(
        String question,
        Plan plan,
        List<HistoryTurn> history,
        String reviewComment,
        int maxSteps,
        String traceId) {
      this.buildCalls++;
      this.receivedComments.add(reviewComment);
      return new BuildOutcome(
          List.of(new LoopStep(0, "查证", "searchAnnouncements", "{}", "营收 1700 亿")), this.draft);
    }

    @Override
    public ReviewVerdict review(String question, BuildOutcome outcome, String traceId) {
      this.reviewCalls++;
      return this.verdicts.removeFirst();
    }
  }

  private TestWorkflowEnvironment env;
  private ScriptedActivities activities;

  @BeforeEach
  void setUp() {
    this.env = TestWorkflowEnvironment.newInstance();
    Worker worker = this.env.newWorker(TASK_QUEUE);
    worker.registerWorkflowImplementationTypes(ResearchPipelineWorkflowImpl.class);
    this.activities = new ScriptedActivities();
    worker.registerActivitiesImplementations(this.activities);
    this.env.start();
  }

  @AfterEach
  void tearDown() {
    this.env.close();
  }

  private ResearchPipelineWorkflow newWorkflow() {
    return this.env
        .getWorkflowClient()
        .newWorkflowStub(
            ResearchPipelineWorkflow.class,
            WorkflowOptions.newBuilder().setTaskQueue(TASK_QUEUE).build());
  }

  private static ResearchRunInput input() {
    return new ResearchRunInput("茅台营收多少", 6, List.of(), "trace-test");
  }

  @Test
  void passOnFirstReviewReturnsDraft() {
    this.activities.verdicts.add(ReviewVerdict.pass());

    ResearchPipelineWorkflow workflow = newWorkflow();
    LoopResult result = workflow.runResearch(input());

    assertThat(result.stopReason()).isEqualTo(StopReason.FINAL_ANSWER);
    assertThat(result.answer()).isEqualTo("草稿答案");
    assertThat(result.steps()).hasSize(1);
    // 串行管线恰好一轮：plan → build → review；首轮无批注
    assertThat(this.activities.planCalls).isEqualTo(1);
    assertThat(this.activities.buildCalls).isEqualTo(1);
    assertThat(this.activities.reviewCalls).isEqualTo(1);
    assertThat(this.activities.receivedComments).containsExactly("");
    assertThat(workflow.status()).isEqualTo("completed");
  }

  @Test
  void rejectCommentRelayedToRebuild() {
    this.activities.verdicts.add(ReviewVerdict.reject("口径串台：中期分红未计入"));
    this.activities.verdicts.add(ReviewVerdict.pass());

    LoopResult result = newWorkflow().runResearch(input());

    assertThat(result.stopReason()).isEqualTo(StopReason.FINAL_ANSWER);
    // 打回批注必须回流 Builder（设计 §1.3：批注供重写时修正）
    assertThat(this.activities.buildCalls).isEqualTo(2);
    assertThat(this.activities.receivedComments).containsExactly("", "口径串台：中期分红未计入");
  }

  @Test
  void rejectionsExhaustedYieldsRefusal() {
    // 打回 ≤2 次仍不过 → 拒答文案（宁可不答不可错答）
    this.activities.verdicts.add(ReviewVerdict.reject("第一批注"));
    this.activities.verdicts.add(ReviewVerdict.reject("第二批注"));
    this.activities.verdicts.add(ReviewVerdict.reject("第三批注"));

    LoopResult result = newWorkflow().runResearch(input());

    assertThat(result.stopReason()).isEqualTo(StopReason.REVIEW_REJECTED);
    assertThat(result.answer()).contains("不予作答");
    // 首轮 + 打回 2 次 = 3 轮 build/review，计划不重拆
    assertThat(this.activities.planCalls).isEqualTo(1);
    assertThat(this.activities.buildCalls).isEqualTo(3);
    assertThat(this.activities.reviewCalls).isEqualTo(3);
  }

  @Test
  void nullDraftSkipsReviewAndReturnsNullAnswer() {
    // 合成失败（draft=null）→ 诚实红线：answer=null 直接收尾，不送复核（无草稿可审）
    this.activities.draft = null;

    LoopResult result = newWorkflow().runResearch(input());

    assertThat(result.stopReason()).isEqualTo(StopReason.MAX_STEPS);
    assertThat(result.answer()).isNull();
    assertThat(this.activities.reviewCalls).isZero();
  }

  @Test
  void redlinePlanWithNoneChannelStillDraftsAndReviews() {
    // Planner 标 none（红线类）→ Builder 不取证（空证据包）直接起草拒答措辞，Reviewer 复核放行
    this.activities.plan = new Plan(List.of(new Plan.SubQuestion("是否值得买入", Channel.NONE)));
    this.activities.verdicts.add(ReviewVerdict.pass());

    LoopResult result = newWorkflow().runResearch(input());

    assertThat(result.stopReason()).isEqualTo(StopReason.FINAL_ANSWER);
    assertThat(this.activities.buildCalls).isEqualTo(1);
  }
}
