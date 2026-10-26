package com.aievolution.loop.temporal;

import com.aievolution.loop.LoopListener;
import com.aievolution.loop.LoopResult;
import com.aievolution.loop.ResearchLoopEngine;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import java.util.List;
import java.util.UUID;

/**
 * 三角色管线引擎门面（W22 #1c）：与 {@link TemporalLoopEngine} 同调用面 （{@link ResearchLoopEngine}），提交 {@link
 * ResearchPipelineWorkflow} 并同步等待——inline 单循环 / temporal 单循环 / temporal 三角色三路可切换对照。
 *
 * <p>workflowId = {@code pipeline-<traceId>}（前缀与单循环 {@code research-} 区分， Temporal UI 可按前缀分臂检索）。
 */
public class TemporalPipelineEngine {

  private final WorkflowClient workflowClient;
  private final LoopListenerRegistry listenerRegistry;
  private final String taskQueue;
  private final boolean hitlEnabled;
  private final long hitlAwaitSeconds;

  public TemporalPipelineEngine(
      WorkflowClient workflowClient,
      LoopListenerRegistry listenerRegistry,
      String taskQueue,
      boolean hitlEnabled,
      long hitlAwaitSeconds) {
    this.workflowClient = workflowClient;
    this.listenerRegistry = listenerRegistry;
    this.taskQueue = taskQueue;
    this.hitlEnabled = hitlEnabled;
    this.hitlAwaitSeconds = hitlAwaitSeconds;
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
        ResearchPipelineWorkflow workflow =
            this.workflowClient.newWorkflowStub(
                ResearchPipelineWorkflow.class,
                WorkflowOptions.newBuilder()
                    .setWorkflowId(workflowId)
                    .setTaskQueue(this.taskQueue)
                    .build());
        LoopResult result =
            workflow.runResearch(
                new ResearchRunInput(
                    question, maxSteps, history, traceId, this.hitlEnabled, this.hitlAwaitSeconds));
        // 与既有引擎同语义：退出回调由引擎层统一收口（管线内子循环不单独 onComplete）
        listener.onComplete(result);
        return result;
      } finally {
        this.listenerRegistry.deregister(workflowId);
      }
    };
  }
}
