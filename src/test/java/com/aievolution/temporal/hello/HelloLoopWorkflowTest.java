package com.aievolution.temporal.hello;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 最小环单测：TestWorkflowEnvironment 进程内测试服务器（带时间跳跃），不依赖外部 dev server。 锁定三条语义：① 三步全执行且结果拼接正确；② step2
 * 首次故障被服务端重试吞掉（结果含 attempts(step2)=2）；③ 完成后 Query 读到终态。
 */
class HelloLoopWorkflowTest {

  private TestWorkflowEnvironment env;

  @BeforeEach
  void setUp() {
    this.env = TestWorkflowEnvironment.newInstance();
    Worker worker = this.env.newWorker(TemporalHelloMain.TASK_QUEUE);
    worker.registerWorkflowImplementationTypes(HelloLoopWorkflowImpl.class);
    worker.registerActivitiesImplementations(new HelloActivitiesImpl());
    this.env.start();
  }

  @AfterEach
  void tearDown() {
    this.env.close();
  }

  @Test
  void runsThreeStepsWithRetryAndQueryableProgress() {
    HelloLoopWorkflow workflow =
        this.env
            .getWorkflowClient()
            .newWorkflowStub(
                HelloLoopWorkflow.class,
                io.temporal.client.WorkflowOptions.newBuilder()
                    .setTaskQueue(TemporalHelloMain.TASK_QUEUE)
                    .build());

    String result = workflow.run("unit-test");

    assertTrue(result.contains("step 1 processed [unit-test]"), "step1 应执行");
    assertTrue(
        result.contains("step 2 processed [unit-test] attempts(step2)=2"), "step2 首次抛错应被重试，第二次成功");
    assertTrue(result.contains("step 3 processed [unit-test]"), "step3 应执行");
    assertEquals("completed", workflow.progress(), "完成后 Query 应读到终态");
  }
}
