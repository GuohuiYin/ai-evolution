package com.aievolution.loop.temporal;

import static org.assertj.core.api.Assertions.assertThat;

import com.aievolution.loop.LoopResult;
import com.aievolution.loop.LoopStep;
import com.aievolution.loop.StopReason;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Temporal 引擎 workflow 单测（W21 #1② 路线 C）：TestWorkflowEnvironment 进程内跑真 workflow， activity
 * 用脚本化桩——逐字对齐 inline 引擎 {@code ResearchLoopTest} 的四条语义： FINAL 收敛 / ACT 执行入轨迹 / 触顶合成 / 合成失败
 * answer=null。
 */
class ResearchLoopWorkflowTest {

  private static final String TASK_QUEUE = "research-loop-test";

  /** 脚本化 activity 桩：nextTurn 按队列出答案，executeStep/synthesize 计数留证 */
  static class ScriptedActivities implements ResearchLoopActivities {
    final Deque<ModelTurnPayload> turns = new ArrayDeque<>();
    String synthesized = "合成答案";
    int executeStepCalls = 0;

    @Override
    public ModelTurnPayload nextTurn(
        String question, List<LoopStep> steps, List<HistoryTurn> history, String traceId) {
      return this.turns.removeFirst();
    }

    @Override
    public String synthesize(
        String question, List<LoopStep> steps, List<HistoryTurn> history, String traceId) {
      return this.synthesized;
    }

    @Override
    public LoopStep executeStep(
        int index, String thought, String tool, String input, String traceId) {
      this.executeStepCalls++;
      return new LoopStep(index, thought, tool, input, "obs-" + index);
    }
  }

  private TestWorkflowEnvironment env;
  private ScriptedActivities activities;

  @BeforeEach
  void setUp() {
    this.env = TestWorkflowEnvironment.newInstance();
    Worker worker = this.env.newWorker(TASK_QUEUE);
    worker.registerWorkflowImplementationTypes(ResearchLoopWorkflowImpl.class);
    this.activities = new ScriptedActivities();
    worker.registerActivitiesImplementations(this.activities);
    this.env.start();
  }

  @AfterEach
  void tearDown() {
    this.env.close();
  }

  private ResearchLoopWorkflow newWorkflow() {
    return this.env
        .getWorkflowClient()
        .newWorkflowStub(
            ResearchLoopWorkflow.class,
            WorkflowOptions.newBuilder().setTaskQueue(TASK_QUEUE).build());
  }

  private static ResearchRunInput input(int maxSteps) {
    return new ResearchRunInput("茅台营收多少", maxSteps, List.of(), "trace-test");
  }

  @Test
  void finalAnswerConvergesWithoutToolCall() {
    this.activities.turns.add(ModelTurnPayload.ofFinal("想好了", "答案"));

    LoopResult result = newWorkflow().runResearch(input(5));

    assertThat(result.stopReason()).isEqualTo(StopReason.FINAL_ANSWER);
    assertThat(result.answer()).isEqualTo("答案");
    assertThat(result.steps()).isEmpty();
    assertThat(this.activities.executeStepCalls).isZero();
  }

  @Test
  void actStepRecordedThenFinal() {
    this.activities.turns.add(ModelTurnPayload.ofAct("查营收", "getRevenue", "{}"));
    this.activities.turns.add(ModelTurnPayload.ofFinal("有数了", "1700 亿"));

    ResearchLoopWorkflow workflow = newWorkflow();
    LoopResult result = workflow.runResearch(input(5));

    assertThat(result.stopReason()).isEqualTo(StopReason.FINAL_ANSWER);
    assertThat(result.steps()).hasSize(1);
    assertThat(result.steps().get(0).observation()).isEqualTo("obs-0");
    // Query 语义：完成后仍可读状态
    assertThat(workflow.currentSteps()).hasSize(1);
    assertThat(workflow.status()).isEqualTo("completed");
  }

  @Test
  void maxStepsTriggersSynthesis() {
    this.activities.turns.add(ModelTurnPayload.ofAct("第一步", "t1", "{}"));
    this.activities.turns.add(ModelTurnPayload.ofAct("第二步", "t2", "{}"));

    LoopResult result = newWorkflow().runResearch(input(2));

    assertThat(result.stopReason()).isEqualTo(StopReason.MAX_STEPS);
    assertThat(result.answer()).isEqualTo("合成答案");
    assertThat(result.steps()).hasSize(2);
  }

  @Test
  void blankSynthesisYieldsNullAnswer() {
    this.activities.turns.add(ModelTurnPayload.ofAct("唯一一步", "t1", "{}"));
    this.activities.synthesized = "";

    LoopResult result = newWorkflow().runResearch(input(1));

    // 诚实红线（同 inline 语义）：合成失败 answer=null，兜底措辞交调用方
    assertThat(result.stopReason()).isEqualTo(StopReason.MAX_STEPS);
    assertThat(result.answer()).isNull();
  }
}
