package com.aievolution.loop.temporal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aievolution.infra.ModelArmRegistry;
import com.aievolution.loop.LoopListener;
import com.aievolution.loop.LoopResult;
import com.aievolution.loop.StopReason;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 三角色管线引擎门面的进程内单测（不依赖真实 Temporal 服务）。重点锁定 W31 #3 F1 修复： 运行期间发生 failover（启动臂=主力、失败时臂=备用）的 workflow
 * 失败自动以同 workflowId 重跑一次、 预算按备用档重取；臂未变的失败原样抛出（确定性故障重试只是浪费）。
 */
class TemporalPipelineEngineTest {

  private static final LoopResult DONE = new LoopResult("答案", List.of(), StopReason.FINAL_ANSWER);

  private static TemporalPipelineEngine engine(WorkflowClient client, ModelArmRegistry arms) {
    return new TemporalPipelineEngine(
        client,
        new LoopListenerRegistry(),
        "pipeline-queue",
        false,
        0,
        EngineArmBudgetTest.PROPS,
        arms);
  }

  @Test
  void failoverDuringRunRetriesOnceWithFallbackBudgets() {
    WorkflowClient client = mock(WorkflowClient.class);
    ResearchPipelineWorkflow workflow = mock(ResearchPipelineWorkflow.class);
    WorkflowFailedException failure = mock(WorkflowFailedException.class);
    when(workflow.runResearch(any())).thenThrow(failure).thenReturn(DONE);
    when(client.newWorkflowStub(eq(ResearchPipelineWorkflow.class), any(WorkflowOptions.class)))
        .thenReturn(workflow);
    // activeArm() 调用序：启动快照=主力 → 首跑预算=主力 → 失败后复查=备用 → 重跑预算=备用
    ModelArmRegistry arms = mock(ModelArmRegistry.class);
    when(arms.activeArm())
        .thenReturn(
            ModelArmRegistry.PRIMARY,
            ModelArmRegistry.PRIMARY,
            ModelArmRegistry.FALLBACK,
            ModelArmRegistry.FALLBACK);

    LoopResult result =
        engine(client, arms).newRun(List.of(), "trace-42", 6, LoopListener.NONE).run("问题");

    assertThat(result).isEqualTo(DONE);
    // 同 workflowId 重跑（Temporal 默认可复用已失败的 workflowId）
    ArgumentCaptor<WorkflowOptions> options = ArgumentCaptor.forClass(WorkflowOptions.class);
    verify(client, times(2)).newWorkflowStub(eq(ResearchPipelineWorkflow.class), options.capture());
    assertThat(options.getAllValues())
        .extracting(WorkflowOptions::getWorkflowId)
        .containsExactly("pipeline-trace-42", "pipeline-trace-42");
    // 重跑按当前臂（备用档）重取预算——夹具备用档 quick=360s / heavy=1200s
    ArgumentCaptor<ResearchRunInput> inputs = ArgumentCaptor.forClass(ResearchRunInput.class);
    verify(workflow, times(2)).runResearch(inputs.capture());
    assertThat(inputs.getAllValues().get(1).budgets()).isEqualTo(new ActivityBudgets(360, 1200));
  }

  @Test
  void failureWithoutArmChangePropagates() {
    // 臂未变（一直是主力）的失败属确定性故障：重试只是浪费，原样抛出
    WorkflowClient client = mock(WorkflowClient.class);
    ResearchPipelineWorkflow workflow = mock(ResearchPipelineWorkflow.class);
    WorkflowFailedException failure = mock(WorkflowFailedException.class);
    when(workflow.runResearch(any())).thenThrow(failure);
    when(client.newWorkflowStub(eq(ResearchPipelineWorkflow.class), any(WorkflowOptions.class)))
        .thenReturn(workflow);
    ModelArmRegistry arms = mock(ModelArmRegistry.class);
    when(arms.activeArm()).thenReturn(ModelArmRegistry.PRIMARY);

    var run = engine(client, arms).newRun(List.of(), "trace-42", 6, LoopListener.NONE);
    assertThatThrownBy(() -> run.run("问题")).isSameAs(failure);
    verify(workflow, times(1)).runResearch(any());
  }

  @Test
  void failureStartingOnFallbackArmPropagates() {
    // 启动时已在备用臂：重跑不会再有「换臂换档」收益，失败原样抛出
    WorkflowClient client = mock(WorkflowClient.class);
    ResearchPipelineWorkflow workflow = mock(ResearchPipelineWorkflow.class);
    WorkflowFailedException failure = mock(WorkflowFailedException.class);
    when(workflow.runResearch(any())).thenThrow(failure);
    when(client.newWorkflowStub(eq(ResearchPipelineWorkflow.class), any(WorkflowOptions.class)))
        .thenReturn(workflow);
    ModelArmRegistry arms = mock(ModelArmRegistry.class);
    when(arms.activeArm()).thenReturn(ModelArmRegistry.FALLBACK);

    var run = engine(client, arms).newRun(List.of(), "trace-42", 6, LoopListener.NONE);
    assertThatThrownBy(() -> run.run("问题")).isSameAs(failure);
    verify(workflow, times(1)).runResearch(any());
  }

  @Test
  void successfulRunCompletesListenerOnce() {
    WorkflowClient client = mock(WorkflowClient.class);
    ResearchPipelineWorkflow workflow = mock(ResearchPipelineWorkflow.class);
    when(workflow.runResearch(any())).thenReturn(DONE);
    when(client.newWorkflowStub(eq(ResearchPipelineWorkflow.class), any(WorkflowOptions.class)))
        .thenReturn(workflow);

    AtomicReference<LoopResult> completed = new AtomicReference<>();
    LoopListener listener =
        new LoopListener() {
          @Override
          public void onComplete(LoopResult result) {
            completed.set(result);
          }
        };

    LoopListenerRegistry registry = new LoopListenerRegistry();
    TemporalPipelineEngine engine =
        new TemporalPipelineEngine(
            client, registry, "pipeline-queue", false, 0, EngineArmBudgetTest.PROPS, null);
    LoopResult result = engine.newRun(List.of(), "trace-42", 6, listener).run("问题");

    assertThat(result).isEqualTo(DONE);
    assertThat(completed.get()).isEqualTo(DONE);
    assertThat(registry.get("pipeline-trace-42")).isSameAs(LoopListener.NONE);
  }
}
