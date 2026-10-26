package com.aievolution.loop.temporal;

import com.aievolution.loop.ReactProtocolParser;
import com.aievolution.loop.ToolRegistry;
import com.aievolution.prompt.PromptLibrary;
import io.micrometer.core.instrument.MeterRegistry;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 三角色管线装配（W22 #1c）：仅当 {@code ai.loop.engine=pipeline} 时启用—— 与 inline（缺省）/ temporal 单循环并列的第三引擎旗标， 双臂
 * eval 期间互斥装配，零风险灰度（TemporalLoopConfig 同款开关）。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "ai.loop.engine", havingValue = "pipeline")
public class TemporalPipelineConfig {

  /** 三角色管线专用任务队列名（与单循环 research-loop 分队列，Temporal UI 可分别观测） */
  public static final String TASK_QUEUE = "research-pipeline";

  @Bean
  public WorkflowServiceStubs pipelineServiceStubs(
      @Value("${ai.loop.temporal.target:127.0.0.1:7233}") String target) {
    return WorkflowServiceStubs.newServiceStubs(
        WorkflowServiceStubsOptions.newBuilder().setTarget(target).build());
  }

  @Bean
  public WorkflowClient pipelineWorkflowClient(WorkflowServiceStubs pipelineServiceStubs) {
    return WorkflowClient.newInstance(pipelineServiceStubs);
  }

  @Bean
  public LoopListenerRegistry pipelineListenerRegistry() {
    return new LoopListenerRegistry();
  }

  @Bean
  public ResearchPipelineActivitiesImpl researchPipelineActivities(
      ChatClient.Builder chatClientBuilder,
      PromptLibrary promptLibrary,
      ToolRegistry toolRegistry,
      @Value("${ai.loop.planner-prompt:planner-v1}") String plannerPromptName,
      @Value("${ai.loop.react-prompt:agent-react-v2}") String reactPromptName,
      @Value("${ai.loop.synthesis-prompt:agent-react-synthesis-v1}") String synthesisPromptName,
      @Value("${ai.loop.reviewer-prompt:reviewer-v1}") String reviewerPromptName,
      ReactProtocolParser parser,
      MeterRegistry meterRegistry,
      LoopListenerRegistry pipelineListenerRegistry,
      com.aievolution.skill.SkillRegistry skillRegistry,
      @Value("${ai.skill.mode:off}") String skillMode) {
    return new ResearchPipelineActivitiesImpl(
        chatClientBuilder,
        promptLibrary,
        toolRegistry,
        plannerPromptName,
        reactPromptName,
        synthesisPromptName,
        reviewerPromptName,
        parser,
        meterRegistry,
        pipelineListenerRegistry,
        skillRegistry,
        skillMode);
  }

  @Bean(destroyMethod = "shutdown")
  public WorkerFactory pipelineWorkerFactory(
      WorkflowClient pipelineWorkflowClient, ResearchPipelineActivitiesImpl activities) {
    WorkerFactory factory = WorkerFactory.newInstance(pipelineWorkflowClient);
    Worker worker = factory.newWorker(TASK_QUEUE);
    worker.registerWorkflowImplementationTypes(ResearchPipelineWorkflowImpl.class);
    worker.registerActivitiesImplementations(activities);
    startQuietly(factory);
    return factory;
  }

  /** Temporal 未达不阻塞启动（W21 既定语义：故障在 run 时以超时暴露）—— 本地起服顺序与测试上下文（无 dev server）都依赖这一宽容 */
  private static void startQuietly(WorkerFactory factory) {
    try {
      factory.start();
    } catch (Exception e) {
      org.slf4j.LoggerFactory.getLogger(TemporalPipelineConfig.class)
          .warn("Temporal 服务未达，worker 未启动（调用时将以超时暴露）: {}", e.getMessage());
    }
  }

  @Bean
  public TemporalPipelineEngine temporalPipelineEngine(
      WorkflowClient pipelineWorkflowClient,
      LoopListenerRegistry pipelineListenerRegistry,
      @Value("${ai.loop.hitl.enabled:false}") boolean hitlEnabled,
      @Value("${ai.loop.hitl.await-timeout:PT2H}") java.time.Duration hitlAwaitTimeout) {
    return new TemporalPipelineEngine(
        pipelineWorkflowClient,
        pipelineListenerRegistry,
        TASK_QUEUE,
        hitlEnabled,
        hitlAwaitTimeout.toSeconds());
  }
}
