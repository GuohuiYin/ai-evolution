package com.aievolution.loop.temporal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aievolution.loop.LoopListener;
import com.aievolution.loop.LoopResult;
import com.aievolution.loop.LoopStep;
import com.aievolution.loop.ResearchLoopEngine;
import com.aievolution.loop.StopReason;
import io.temporal.client.WorkflowClient;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** 引擎门面的进程内单测：workflowId 生成、listener 桥注册/回调/摘除，均不依赖真实 Temporal 服务。 */
class TemporalLoopEngineTest {

  @Test
  void runWiresListenerBridgeAndReturnsResult() {
    WorkflowClient client = mock(WorkflowClient.class);
    ResearchLoopWorkflow workflow = mock(ResearchLoopWorkflow.class);
    LoopResult expected =
        new LoopResult(
            "答案", List.of(new LoopStep(0, "t", "tool", "in", "obs")), StopReason.FINAL_ANSWER);
    when(workflow.runResearch(any())).thenReturn(expected);
    when(client.newWorkflowStub(
            eq(ResearchLoopWorkflow.class), any(io.temporal.client.WorkflowOptions.class)))
        .thenReturn(workflow);

    LoopListenerRegistry registry = new LoopListenerRegistry();
    TemporalLoopEngine engine = new TemporalLoopEngine(client, registry, "research-loop");

    AtomicReference<LoopResult> completed = new AtomicReference<>();
    LoopListener listener =
        new LoopListener() {
          @Override
          public void onComplete(LoopResult result) {
            completed.set(result);
          }
        };

    ResearchLoopEngine run = engine.newRun(List.of(), "trace-42", 6, listener);
    LoopResult result = run.run("问题");

    assertThat(result).isEqualTo(expected);
    assertThat(completed.get()).isEqualTo(expected);
    // listener 桥在用后即摘——不留泄漏
    assertThat(registry.get("research-trace-42")).isSameAs(LoopListener.NONE);
  }
}
