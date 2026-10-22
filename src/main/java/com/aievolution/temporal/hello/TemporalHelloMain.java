package com.aievolution.temporal.hello;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

/**
 * W21 #1 最小环真机实录入口：连本地 dev server（localhost:7233），起 worker → 执行 workflow → 打印结果与进度查询。
 *
 * <p>独立 main 而非织入 Spring 启动——学习环不动主应用行为；正式集成方案待 #1② 裁决后另行设计。
 *
 * <p>用法：先 `temporal server start-dev`，再运行本类；UI：<a href="http://localhost:8233">...</a>
 */
public final class TemporalHelloMain {

  static final String TASK_QUEUE = "hello-task-queue";

  private TemporalHelloMain() {}

  public static void main(String[] args) {
    WorkflowServiceStubs service = WorkflowServiceStubs.newLocalServiceStubs();
    WorkflowClient client = WorkflowClient.newInstance(service);
    WorkerFactory factory = WorkerFactory.newInstance(client);
    Worker worker = factory.newWorker(TASK_QUEUE);
    worker.registerWorkflowImplementationTypes(HelloLoopWorkflowImpl.class);
    worker.registerActivitiesImplementations(new HelloActivitiesImpl());
    factory.start();

    HelloLoopWorkflow workflow =
        client.newWorkflowStub(
            HelloLoopWorkflow.class, WorkflowOptions.newBuilder().setTaskQueue(TASK_QUEUE).build());

    String result = workflow.run("ai-evolution W21 最小环");
    System.out.println("--- workflow result ---");
    System.out.println(result);
    System.out.println("--- progress query after completion ---");
    System.out.println(workflow.progress());

    factory.shutdown();
    service.shutdown();
  }
}
