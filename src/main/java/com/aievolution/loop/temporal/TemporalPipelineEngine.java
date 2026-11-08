package com.aievolution.loop.temporal;

import com.aievolution.infra.ArmTimeoutProperties;
import com.aievolution.infra.ModelArmRegistry;
import com.aievolution.loop.LoopListener;
import com.aievolution.loop.LoopResult;
import com.aievolution.loop.ResearchLoopEngine;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 三角色管线引擎门面（W22 #1c）：与 {@link TemporalLoopEngine} 同调用面 （{@link ResearchLoopEngine}），提交 {@link
 * ResearchPipelineWorkflow} 并同步等待——inline 单循环 / temporal 单循环 / temporal 三角色三路可切换对照。
 *
 * <p>workflowId = {@code pipeline-<traceId>}（前缀与单循环 {@code research-} 区分， Temporal UI 可按前缀分臂检索）。
 *
 * <p>W31 #1 failover 超时联动：每次运行启动时查当前活跃模型臂（{@link ModelArmRegistry}， 冷却窗判定），按臂查预算表（{@link
 * ArmTimeoutProperties}）填入 {@link ResearchRunInput#budgets()}——换臂即换档。 failover 关闭时无登记 bean，按主力臂处理。
 *
 * <p>W31 #3 引擎级重试（F1 修复）：运行期间发生 failover（启动臂=主力、失败时臂=备用）的 {@link WorkflowFailedException} 自动以同
 * workflowId 重跑一次（Temporal 默认可复用已失败的 workflowId）， 预算按当前臂（备用档）重取——启动时臂正常、运行中主力断流导致的整单 500 不再直接抛给用户。
 * 无 failover（臂未变）的失败原样抛出：确定性故障重试只是浪费。
 */
public class TemporalPipelineEngine {

  private static final Logger log = LoggerFactory.getLogger(TemporalPipelineEngine.class);

  private final WorkflowClient workflowClient;
  private final LoopListenerRegistry listenerRegistry;
  private final String taskQueue;
  private final boolean hitlEnabled;
  private final long hitlAwaitSeconds;
  private final HitlMode hitlMode;
  private final ArmTimeoutProperties armTimeouts;
  private final ModelArmRegistry armRegistry;

  public TemporalPipelineEngine(
      WorkflowClient workflowClient,
      LoopListenerRegistry listenerRegistry,
      String taskQueue,
      boolean hitlEnabled,
      long hitlAwaitSeconds,
      ArmTimeoutProperties armTimeouts,
      ModelArmRegistry armRegistry) {
    this(
        workflowClient,
        listenerRegistry,
        taskQueue,
        hitlEnabled,
        hitlAwaitSeconds,
        armTimeouts,
        armRegistry,
        HitlMode.ATTENDED);
  }

  public TemporalPipelineEngine(
      WorkflowClient workflowClient,
      LoopListenerRegistry listenerRegistry,
      String taskQueue,
      boolean hitlEnabled,
      long hitlAwaitSeconds,
      ArmTimeoutProperties armTimeouts,
      ModelArmRegistry armRegistry,
      HitlMode hitlMode) {
    this.workflowClient = workflowClient;
    this.listenerRegistry = listenerRegistry;
    this.taskQueue = taskQueue;
    this.hitlEnabled = hitlEnabled;
    this.hitlAwaitSeconds = hitlAwaitSeconds;
    this.armTimeouts = armTimeouts;
    this.armRegistry = armRegistry;
    this.hitlMode = hitlMode;
  }

  /** 当前活跃臂：failover 关闭（无登记 bean）按主力臂 */
  String currentArm() {
    return this.armRegistry != null ? this.armRegistry.activeArm() : ModelArmRegistry.PRIMARY;
  }

  /** 当前臂的预算档（运行启动时决议） */
  ActivityBudgets currentBudgets() {
    var budget = this.armTimeouts.forArm(currentArm());
    return new ActivityBudgets(
        budget.quickActivityBudget().toSeconds(), budget.heavyActivityBudget().toSeconds());
  }

  public ResearchLoopEngine newRun(
      List<HistoryTurn> history, String traceId, int maxSteps, LoopListener listener) {
    if (maxSteps < 1) {
      throw new IllegalArgumentException("maxSteps 必须 ≥ 1: " + maxSteps);
    }
    return question -> {
      String workflowId =
          "pipeline-" + (traceId != null && !traceId.isBlank() ? traceId : UUID.randomUUID());
      this.listenerRegistry.register(workflowId, listener);
      try {
        LoopResult result = awaitRun(workflowId, question, history, traceId, maxSteps);
        // 与既有引擎同语义：退出回调由引擎层统一收口（管线内子循环不单独 onComplete）
        listener.onComplete(result);
        return result;
      } finally {
        this.listenerRegistry.deregister(workflowId);
      }
    };
  }

  /**
   * 提交并同步等待运行；运行期间发生 failover（启动臂=主力、失败时臂=备用）则以同 workflowId 重跑一次 （W31 #3 F1
   * 修复），预算按当前臂重取。重跑只此一次——备用臂上再失败属于真故障，原样抛出。
   */
  private LoopResult awaitRun(
      String workflowId, String question, List<HistoryTurn> history, String traceId, int maxSteps) {
    String armAtStart = currentArm();
    try {
      return runOnce(workflowId, question, history, traceId, maxSteps);
    } catch (WorkflowFailedException e) {
      boolean failoverDuringRun =
          ModelArmRegistry.PRIMARY.equals(armAtStart)
              && ModelArmRegistry.FALLBACK.equals(currentArm());
      if (!failoverDuringRun) {
        throw e;
      }
      log.warn(
          "运行期间发生 failover，同 workflowId 重跑一次 workflowId={} traceId={} cause={}",
          workflowId,
          traceId,
          e.getMessage());
      return runOnce(workflowId, question, history, traceId, maxSteps);
    }
  }

  private LoopResult runOnce(
      String workflowId, String question, List<HistoryTurn> history, String traceId, int maxSteps) {
    ResearchPipelineWorkflow workflow =
        this.workflowClient.newWorkflowStub(
            ResearchPipelineWorkflow.class,
            WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(this.taskQueue)
                .build());
    return workflow.runResearch(
        new ResearchRunInput(
            question,
            maxSteps,
            history,
            traceId,
            this.hitlEnabled,
            this.hitlAwaitSeconds,
            currentBudgets(),
            this.hitlMode));
  }
}
