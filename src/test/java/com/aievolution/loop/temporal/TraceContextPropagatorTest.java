package com.aievolution.loop.temporal;

import static org.assertj.core.api.Assertions.assertThat;

import com.aievolution.infra.TraceContextPropagation;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityOptions;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestEnvironmentOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactoryOptions;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Temporal 链路上下文传播测试（W25 #1a 收尾）：TestWorkflowEnvironment 进程内跑真 workflow—— 请求侧开一个真实 OTel span 启动
 * workflow，探针 activity 回读当前 traceId， 断言与请求侧一致（传播接通）；无父级启动则断言 activity 侧无有效 span（降级直执）。
 */
class TraceContextPropagatorTest {

  private static final String TASK_QUEUE = "trace-context-test";

  @ActivityInterface
  public interface TraceProbeActivity {
    /** 回读 activity 线程上的 OTel 当前 traceId（无有效 span 返回 "none"） */
    String currentTraceId();
  }

  public static class TraceProbeActivityImpl implements TraceProbeActivity {
    @Override
    public String currentTraceId() {
      var sc = Span.current().getSpanContext();
      return sc.isValid() ? sc.getTraceId() : "none";
    }
  }

  @WorkflowInterface
  public interface TraceProbeWorkflow {
    @WorkflowMethod
    String run();
  }

  public static class TraceProbeWorkflowImpl implements TraceProbeWorkflow {
    @Override
    public String run() {
      TraceProbeActivity activity =
          Workflow.newActivityStub(
              TraceProbeActivity.class,
              ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofSeconds(5)).build());
      return activity.currentTraceId();
    }
  }

  private TestWorkflowEnvironment env;

  @AfterEach
  void tearDown() {
    if (this.env != null) {
      this.env.close();
    }
  }

  private String runProbe() {
    this.env =
        TestWorkflowEnvironment.newInstance(
            TestEnvironmentOptions.newBuilder()
                .setWorkflowClientOptions(
                    WorkflowClientOptions.newBuilder()
                        .setContextPropagators(List.of(new TraceContextPropagator()))
                        .build())
                .setWorkerFactoryOptions(
                    WorkerFactoryOptions.newBuilder()
                        .setWorkerInterceptors(new TraceContextWorkerInterceptor())
                        .build())
                .build());
    Worker worker = this.env.newWorker(TASK_QUEUE);
    worker.registerWorkflowImplementationTypes(TraceProbeWorkflowImpl.class);
    worker.registerActivitiesImplementations(new TraceProbeActivityImpl());
    this.env.start();
    TraceProbeWorkflow wf =
        this.env
            .getWorkflowClient()
            .newWorkflowStub(
                TraceProbeWorkflow.class,
                WorkflowOptions.newBuilder().setTaskQueue(TASK_QUEUE).build());
    return wf.run();
  }

  @Test
  void activityJoinsCallerTraceWhenParentSpanPresent() {
    SdkTracerProvider provider = SdkTracerProvider.builder().build();
    Span parent = provider.get("test").spanBuilder("probe-parent").startSpan();
    String expectedTraceId = parent.getSpanContext().getTraceId();
    try (Scope ignored = parent.makeCurrent()) {
      assertThat(runProbe()).isEqualTo(expectedTraceId);
    } finally {
      parent.end();
      provider.close();
    }
  }

  @Test
  void activityRunsWithoutParentWhenNoSpanPresent() {
    assertThat(Span.current().getSpanContext().isValid()).isFalse();
    assertThat(runProbe()).isEqualTo("none");
  }

  @Test
  void serializeDeserializeRoundTrip() {
    TraceContextPropagator propagator = new TraceContextPropagator();
    Map<String, io.temporal.api.common.v1.Payload> header =
        propagator.serializeContext("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
    assertThat(header).containsKey("traceparent");
    assertThat(propagator.deserializeContext(header))
        .isEqualTo("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
    // 无上下文化合：序列化空表、反序列化 null——降级不炸
    assertThat(propagator.serializeContext(null)).isEmpty();
    assertThat(propagator.deserializeContext(Map.of())).isNull();
  }

  @Test
  void remoteParentRestoreIsNullSafe() {
    try (Scope ignored = TraceContextPropagation.withRemoteParent("not-a-traceparent")) {
      assertThat(Span.current().getSpanContext().isValid()).isFalse();
    }
    try (Scope ignored =
        TraceContextPropagation.withRemoteParent(
            "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")) {
      var sc = Span.current().getSpanContext();
      assertThat(sc.isValid()).isTrue();
      assertThat(sc.getTraceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
      assertThat(sc.isRemote()).isTrue();
    }
  }
}
