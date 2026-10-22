package com.aievolution.loop.temporal;

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

  public TemporalLoopEngine(
      WorkflowClient workflowClient, LoopListenerRegistry listenerRegistry, String taskQueue) {
    this.workflowClient = workflowClient;
    this.listenerRegistry = listenerRegistry;
    this.taskQueue = taskQueue;
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
            workflow.runResearch(new ResearchRunInput(question, maxSteps, history, traceId));
        // 与 inline 引擎 finish() 同语义：退出回调（含收敛与触顶，stopReason 区分）
        listener.onComplete(result);
        return result;
      } finally {
        this.listenerRegistry.deregister(workflowId);
      }
    };
  }
}
