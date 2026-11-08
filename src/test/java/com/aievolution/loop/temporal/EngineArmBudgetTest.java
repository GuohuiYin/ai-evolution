package com.aievolution.loop.temporal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aievolution.infra.ArmTimeoutProperties;
import com.aievolution.infra.ModelArmRegistry;
import com.aievolution.loop.LoopListener;
import com.aievolution.loop.LoopResult;
import com.aievolution.loop.StopReason;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 引擎按臂换档单测（W31 #1 步骤 2c）：workflow 启动前查活跃臂 → 预算档随入参进 workflow。 锁定三态：备用臂取备用档 / 主力臂取主力档 / failover
 * 关闭（无登记）按主力档。
 */
class EngineArmBudgetTest {

  static final ArmTimeoutProperties PROPS =
      new ArmTimeoutProperties(
          new ArmTimeoutProperties.ArmBudget(
              Duration.ofMinutes(2), Duration.ofMinutes(10), Duration.ofMinutes(3)),
          new ArmTimeoutProperties.ArmBudget(
              Duration.ofMinutes(6), Duration.ofMinutes(20), Duration.ofMinutes(6)));

  private static final LoopResult DONE = new LoopResult("答案", List.of(), StopReason.FINAL_ANSWER);

  @Test
  void pipelineEngineUsesFallbackBudgetWhenFallbackArmActive() {
    WorkflowClient client = mock(WorkflowClient.class);
    ResearchPipelineWorkflow workflow = mock(ResearchPipelineWorkflow.class);
    when(client.newWorkflowStub(eq(ResearchPipelineWorkflow.class), any(WorkflowOptions.class)))
        .thenReturn(workflow);
    when(workflow.runResearch(any())).thenReturn(DONE);
    var engine =
        new TemporalPipelineEngine(
            client,
            new LoopListenerRegistry(),
            "q",
            false,
            0,
            PROPS,
            () -> ModelArmRegistry.FALLBACK);

    engine.newRun(List.of(), "t1", 6, mock(LoopListener.class)).run("茅台营收");

    ArgumentCaptor<ResearchRunInput> captor = ArgumentCaptor.forClass(ResearchRunInput.class);
    verify(workflow).runResearch(captor.capture());
    assertThat(captor.getValue().budgets()).isEqualTo(new ActivityBudgets(360, 1200));
  }

  @Test
  void pipelineEngineUsesPrimaryBudgetOnPrimaryArm() {
    WorkflowClient client = mock(WorkflowClient.class);
    ResearchPipelineWorkflow workflow = mock(ResearchPipelineWorkflow.class);
    when(client.newWorkflowStub(eq(ResearchPipelineWorkflow.class), any(WorkflowOptions.class)))
        .thenReturn(workflow);
    when(workflow.runResearch(any())).thenReturn(DONE);
    var engine =
        new TemporalPipelineEngine(
            client,
            new LoopListenerRegistry(),
            "q",
            false,
            0,
            PROPS,
            () -> ModelArmRegistry.PRIMARY);

    engine.newRun(List.of(), "t1", 6, mock(LoopListener.class)).run("茅台营收");

    ArgumentCaptor<ResearchRunInput> captor = ArgumentCaptor.forClass(ResearchRunInput.class);
    verify(workflow).runResearch(captor.capture());
    assertThat(captor.getValue().budgets()).isEqualTo(new ActivityBudgets(120, 600));
  }

  @Test
  void loopEngineDefaultsToPrimaryBudgetWhenNoRegistry() {
    // failover 关闭（登记 bean 缺席）→ 主力档，不炸不静默
    WorkflowClient client = mock(WorkflowClient.class);
    ResearchLoopWorkflow workflow = mock(ResearchLoopWorkflow.class);
    when(client.newWorkflowStub(eq(ResearchLoopWorkflow.class), any(WorkflowOptions.class)))
        .thenReturn(workflow);
    when(workflow.runResearch(any())).thenReturn(DONE);
    var engine = new TemporalLoopEngine(client, new LoopListenerRegistry(), "q", PROPS, null);

    engine.newRun(List.of(), "t1", 6, mock(LoopListener.class)).run("茅台营收");

    ArgumentCaptor<ResearchRunInput> captor = ArgumentCaptor.forClass(ResearchRunInput.class);
    verify(workflow).runResearch(captor.capture());
    assertThat(captor.getValue().budgets()).isEqualTo(new ActivityBudgets(120, 600));
  }
}
