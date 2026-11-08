package com.aievolution.loop.temporal;

import com.aievolution.infra.ArmTimeoutProperties;
import com.aievolution.infra.ModelArmRegistry;
import com.aievolution.loop.LoopListener;
import com.aievolution.loop.LoopResult;
import com.aievolution.loop.ResearchLoopEngine;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import java.util.List;
import java.util.UUID;

/**
 * Temporal 引擎门面（W21 #1② 路线 C）：把一轮研究运行提交为 workflow 并同步等待结果。 每请求经 {@link #newRun}
 * 生成捕获本请求上下文（历史/traceId/listener/步数）的引擎实例， 与 inline 引擎共用 {@link ResearchLoopEngine} 调用面。
 *
 * <p>workflowId = {@code research-<traceId>}（无 traceId 退 UUID）——可读、可在 Temporal UI 直接检索，与日志 traceId
 * 同源定位。
 */
public class TemporalLoopEngine {

  private final WorkflowClient workflowClient;
  private final LoopListenerRegistry listenerRegistry;
  private final String taskQueue;
  private final ArmTimeoutProperties armTimeouts;
  private final ModelArmRegistry armRegistry;

  public TemporalLoopEngine(
      WorkflowClient workflowClient,
      LoopListenerRegistry listenerRegistry,
      String taskQueue,
      ArmTimeoutProperties armTimeouts,
      ModelArmRegistry armRegistry) {
    this.workflowClient = workflowClient;
    this.listenerRegistry = listenerRegistry;
    this.taskQueue = taskQueue;
    this.armTimeouts = armTimeouts;
    this.armRegistry = armRegistry;
  }

  /** 当前臂的预算档（运行启动时决议，W31 #1）：failover 关闭（无登记 bean）按主力臂 */
  ActivityBudgets currentBudgets() {
    String arm = this.armRegistry != null ? this.armRegistry.activeArm() : ModelArmRegistry.PRIMARY;
    var budget = this.armTimeouts.forArm(arm);
    return new ActivityBudgets(
        budget.quickActivityBudget().toSeconds(), budget.heavyActivityBudget().toSeconds());
  }

  public ResearchLoopEngine newRun(
      List<HistoryTurn> history, String traceId, int maxSteps, LoopListener listener) {
    if (maxSteps < 1) {
      // 与 ResearchLoop 构造同语义：上限必须为正，配置错误 fail-fast
      throw new IllegalArgumentException("maxSteps 必须 ≥ 1: " + maxSteps);
    }
    return question -> {
      String workflowId =
          "research-" + (traceId != null && !traceId.isBlank() ? traceId : UUID.randomUUID());
      this.listenerRegistry.register(workflowId, listener);
      try {
        ResearchLoopWorkflow workflow =
            this.workflowClient.newWorkflowStub(
                ResearchLoopWorkflow.class,
                WorkflowOptions.newBuilder()
                    .setWorkflowId(workflowId)
                    .setTaskQueue(this.taskQueue)
                    .build());
        LoopResult result =
            workflow.runResearch(
                new ResearchRunInput(
                    question, maxSteps, history, traceId, false, 0, currentBudgets()));
        // 与 inline 引擎 finish() 同语义：退出回调（含收敛与触顶，stopReason 区分）
        listener.onComplete(result);
        return result;
      } finally {
        this.listenerRegistry.deregister(workflowId);
      }
    };
  }
}
