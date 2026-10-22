package com.aievolution.temporal.hello;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;

/**
 * {@link HelloLoopWorkflow} 实现：三步循环，每步调 activity，进度写入 workflow 状态供 Query。
 *
 * <p>注意 workflow 代码的确定性约束：不能直接用 {@code System.currentTimeMillis()}、线程、 随机数等非确定性源（重放会分叉）；本实现只调
 * activity stub 与 Workflow 提供的 API。
 */
public class HelloLoopWorkflowImpl implements HelloLoopWorkflow {

  private volatile String progress = "not-started";

  private final HelloActivities activities =
      Workflow.newActivityStub(
          HelloActivities.class,
          ActivityOptions.newBuilder()
              .setStartToCloseTimeout(Duration.ofSeconds(10))
              // 重试策略：最多 3 次尝试；失败由 Temporal 服务端调度重试，workflow 代码零感知
              .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(3).build())
              .build());

  @Override
  public String run(String input) {
    StringBuilder out = new StringBuilder();
    for (int i = 1; i <= 3; i++) {
      this.progress = "step-" + i + "-running";
      out.append(this.activities.executeStep(input, i)).append('\n');
      this.progress = "step-" + i + "-done";
    }
    this.progress = "completed";
    return out.toString().trim();
  }

  @Override
  public String progress() {
    return this.progress;
  }
}
