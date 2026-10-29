package com.aievolution.loop.temporal;

import com.aievolution.loop.ReactProtocolParser;
import com.aievolution.loop.ToolRegistry;
import com.aievolution.prompt.PromptLibrary;
import io.micrometer.core.instrument.MeterRegistry;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.worker.WorkerFactoryOptions;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Temporal 引擎装配（W21 #1② 路线 C）：仅当 {@code ai.loop.engine=temporal} 时启用， inline 为缺省——主链路零风险灰度（Tushare
 * {@code ai.stock.data-source} 同款配置开关）。
 *
 * <p>worker 与 Spring 同生命周期（destroyMethod=shutdown）；Temporal 服务未达时不阻塞启动， 故障在 run 时以超时暴露（dev 环境经
 * {@code temporal server start-dev} 起本地服务）。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "ai.loop.engine", havingValue = "temporal")
public class TemporalLoopConfig {

  /** 研究循环专用任务队列名 */
  public static final String TASK_QUEUE = "research-loop";

  @Bean
  public WorkflowServiceStubs temporalServiceStubs(
      @Value("${ai.loop.temporal.target:127.0.0.1:7233}") String target) {
    return WorkflowServiceStubs.newServiceStubs(
        WorkflowServiceStubsOptions.newBuilder().setTarget(target).build());
  }

  @Bean
  public WorkflowClient temporalWorkflowClient(WorkflowServiceStubs serviceStubs) {
    // 链路上下文随 workflow/activity header 传播（W25 #1a）：worker 侧 activity 不再是孤儿 root
    return WorkflowClient.newInstance(
        serviceStubs,
        WorkflowClientOptions.newBuilder()
            .setContextPropagators(List.of(new TraceContextPropagator()))
            .build());
  }

  @Bean
  public LoopListenerRegistry loopListenerRegistry() {
    return new LoopListenerRegistry();
  }

  @Bean
  public ResearchLoopActivitiesImpl researchLoopActivities(
      ChatClient.Builder chatClientBuilder,
      PromptLibrary promptLibrary,
      ToolRegistry toolRegistry,
      @Value("${ai.loop.react-prompt:agent-react-v2}") String promptName,
      @Value("${ai.loop.synthesis-prompt:agent-react-synthesis-v1}") String synthesisPromptName,
      ReactProtocolParser parser,
      MeterRegistry meterRegistry,
      LoopListenerRegistry listenerRegistry,
      com.aievolution.skill.SkillRegistry skillRegistry,
      @Value("${ai.skill.mode:off}") String skillMode) {
    return new ResearchLoopActivitiesImpl(
        chatClientBuilder,
        promptLibrary,
        toolRegistry,
        promptName,
        synthesisPromptName,
        parser,
        meterRegistry,
        listenerRegistry,
        skillRegistry,
        skillMode);
  }

  @Bean(destroyMethod = "shutdown")
  public WorkerFactory temporalWorkerFactory(
      WorkflowClient workflowClient, ResearchLoopActivitiesImpl activities) {
    WorkerFactory factory =
        WorkerFactory.newInstance(
            workflowClient,
            WorkerFactoryOptions.newBuilder()
                .setWorkerInterceptors(new TraceContextWorkerInterceptor())
                .build());
    Worker worker = factory.newWorker(TASK_QUEUE);
    worker.registerWorkflowImplementationTypes(ResearchLoopWorkflowImpl.class);
    worker.registerActivitiesImplementations(activities);
    startQuietly(factory);
    return factory;
  }

  /** 兑现本类 javadoc 的承诺：Temporal 未达不阻塞启动（三角色管线 TemporalPipelineConfig 同款） */
  private static void startQuietly(WorkerFactory factory) {
    try {
      factory.start();
    } catch (Exception e) {
      org.slf4j.LoggerFactory.getLogger(TemporalLoopConfig.class)
          .warn("Temporal 服务未达，worker 未启动（调用时将以超时暴露）: {}", e.getMessage());
    }
  }

  @Bean
  public TemporalLoopEngine temporalLoopEngine(
      WorkflowClient workflowClient, LoopListenerRegistry listenerRegistry) {
    return new TemporalLoopEngine(workflowClient, listenerRegistry, TASK_QUEUE);
  }
}
