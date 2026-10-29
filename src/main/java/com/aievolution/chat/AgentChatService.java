package com.aievolution.chat;

import com.aievolution.compliance.Disclaimers;
import com.aievolution.infra.TraceIdFilter;
import com.aievolution.infra.TraceSpans;
import com.aievolution.loop.DeepSeekResearchModel;
import com.aievolution.loop.LoopListener;
import com.aievolution.loop.LoopResult;
import com.aievolution.loop.LoopStep;
import com.aievolution.loop.ReactProtocolParser;
import com.aievolution.loop.ResearchLoop;
import com.aievolution.loop.ResearchLoopEngine;
import com.aievolution.loop.ResearchModel;
import com.aievolution.loop.ToolRegistry;
import com.aievolution.prompt.PromptLibrary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Tracer;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

/**
 * 工具增强的对话服务（W5 起）。W11 #3 起由显式 {@link ResearchLoop} 驱动： 模型的工具调用决策经 ReAct 协议解析后逐步执行，轨迹可见、步数可控（{@code
 * ai.loop.max-steps}）。
 *
 * <p>与 {@link RagChatService} 的分工：RAG 走知识库语义检索（通路一），本服务走工具实时取数 （通路二）的显式研究循环。
 *
 * <p>会话记忆改为手工读写（原 advisor 会在循环每步重复追加同一条 user 消息）： 跑前取窗口历史注入模型上下文，跑完把本轮问答写回。
 */
@Service
public class AgentChatService implements ChatService {

  // 红线 01：免责声明引用全项目单点定义（约定 A12），不各自拷贝
  private static final String DISCLAIMER = "\n\n" + Disclaimers.AI_GENERATED;

  private final ChatClient.Builder chatClientBuilder;
  private final ToolRegistry toolRegistry;
  private final PromptLibrary promptLibrary;
  private final ReactProtocolParser parser;
  private final ChatMemory chatMemory;
  private final MeterRegistry meterRegistry;
  private final String promptName;
  private final String synthesisPromptName;
  private final int maxSteps;
  private final String loopEngine;
  private final String skillMode;
  private final com.aievolution.skill.SkillRegistry skillRegistry;
  private final org.springframework.beans.factory.ObjectProvider<
          com.aievolution.loop.temporal.TemporalLoopEngine>
      temporalLoopEngine;
  private final org.springframework.beans.factory.ObjectProvider<
          com.aievolution.loop.temporal.TemporalPipelineEngine>
      temporalPipelineEngine;
  private final TraceSpans traceSpans;
  private final org.springframework.beans.factory.ObjectProvider<Tracer> tracerProvider;

  public AgentChatService(
      ChatClient.Builder chatClientBuilder,
      ToolRegistry toolRegistry,
      PromptLibrary promptLibrary,
      ReactProtocolParser parser,
      ChatMemory chatMemory,
      MeterRegistry meterRegistry,
      @Value("${ai.loop.react-prompt:agent-react-v2}") String promptName,
      @Value("${ai.loop.synthesis-prompt:agent-react-synthesis-v1}") String synthesisPromptName,
      @Value("${ai.loop.max-steps:6}") int maxSteps,
      @Value("${ai.loop.engine:inline}") String loopEngine,
      @Value("${ai.skill.mode:off}") String skillMode,
      com.aievolution.skill.SkillRegistry skillRegistry,
      org.springframework.beans.factory.ObjectProvider<
              com.aievolution.loop.temporal.TemporalLoopEngine>
          temporalLoopEngine,
      org.springframework.beans.factory.ObjectProvider<
              com.aievolution.loop.temporal.TemporalPipelineEngine>
          temporalPipelineEngine,
      TraceSpans traceSpans,
      org.springframework.beans.factory.ObjectProvider<Tracer> tracerProvider) {
    if (!promptLibrary.exists(promptName)) {
      // prompt 缺失是部署事故，启动即 fail-fast
      throw new IllegalStateException("ReAct prompt 模板不存在: " + promptName);
    }
    this.chatClientBuilder = chatClientBuilder;
    this.toolRegistry = toolRegistry;
    this.promptLibrary = promptLibrary;
    this.parser = parser;
    this.chatMemory = chatMemory;
    this.meterRegistry = meterRegistry;
    this.promptName = promptName;
    this.synthesisPromptName = synthesisPromptName;
    this.maxSteps = maxSteps;
    this.loopEngine = loopEngine;
    this.skillMode = skillMode;
    this.skillRegistry = skillRegistry;
    this.temporalLoopEngine = temporalLoopEngine;
    this.temporalPipelineEngine = temporalPipelineEngine;
    this.traceSpans = traceSpans;
    this.tracerProvider = tracerProvider;
  }

  public ChatAnswer chat(String message, String conversationId) {
    LoopResult result = newLoop(conversationId, LoopListener.NONE).run(message);
    String answer = answerOf(result);
    chatMemory.add(conversationId, List.of(new UserMessage(message), new AssistantMessage(answer)));
    return new ChatAnswer(answer + DISCLAIMER, List.of());
  }

  @Override
  public Flux<ChatStreamPart> chatStream(String message, String conversationId) {
    // 轨迹实时推送：循环在弹性线程上跑，每步完成即经 Sinks 发射 trajectory 事件；
    // 终答不再逐字流式（ReAct 产品的自然形态：逐步轨迹 + 一次性结论）
    Sinks.Many<ChatStreamPart> sink = Sinks.many().unicast().onBackpressureBuffer();
    // MDC 不跨线程：请求线程的 traceId 手工交接，研究轨迹日志不断链
    String traceId = MDC.get(TraceIdFilter.MDC_KEY);
    // OTel 上下文同样不跨线程（W25 #1a）：当前 span 手工交接进弹性线程，
    // react.step 等子段才能挂回请求 trace 而非沦为孤儿 root
    Tracer tracer = tracerProvider.getIfAvailable();
    io.micrometer.tracing.Span parentSpan = tracer == null ? null : tracer.currentSpan();
    LoopListener listener =
        new LoopListener() {
          @Override
          public void onStep(LoopStep step) {
            sink.tryEmitNext(
                new ChatStreamPart.Trajectory(
                    step.index(), step.thought(), step.tool(), step.input(), step.observation()));
          }
        };
    Runnable work =
        () -> {
          if (traceId != null) {
            MDC.put(TraceIdFilter.MDC_KEY, traceId);
          }
          // parentSpan 为 null 时资源为 null——try-with-resources 跳过关闭，安全
          try (Tracer.SpanInScope ignored =
              parentSpan == null ? null : tracer.withSpan(parentSpan)) {
            LoopResult result = newLoop(conversationId, listener).run(message);
            String answer = answerOf(result);
            chatMemory.add(
                conversationId, List.of(new UserMessage(message), new AssistantMessage(answer)));
            sink.tryEmitNext(new ChatStreamPart.Delta(answer + DISCLAIMER));
            sink.tryEmitNext(new ChatStreamPart.Complete(conversationId, List.of()));
            sink.tryEmitComplete();
          } catch (Exception e) {
            sink.tryEmitError(e);
          } finally {
            MDC.remove(TraceIdFilter.MDC_KEY);
          }
        };
    Schedulers.boundedElastic().schedule(work);
    return sink.asFlux();
  }

  /** 触顶兜底话术：合成步也失败时（W20 #1 裁决①B），如实告知并指向已留痕的轨迹（红线措辞归 chat 域） */
  private String answerOf(LoopResult result) {
    if (result.answer() != null) {
      return result.answer();
    }
    return "本次研究未能在限定的 %d 步内得出结论。已完成的查证步骤可在研究轨迹中查看；建议缩小问题范围后重试。".formatted(maxSteps);
  }

  private ResearchLoopEngine newLoop(String conversationId, LoopListener listener) {
    List<Message> history = chatMemory.get(conversationId);
    // W21 #1② 路线 C：双引擎旗标（inline 缺省 / temporal 事件历史 checkpoint + 断点恢复）
    if ("temporal".equals(this.loopEngine)) {
      com.aievolution.loop.temporal.TemporalLoopEngine engine =
          this.temporalLoopEngine.getIfAvailable();
      if (engine == null) {
        // 旗标与装配同键（TemporalLoopConfig @ConditionalOnProperty），缺 Bean 即配置事故
        throw new IllegalStateException("ai.loop.engine=temporal 但 Temporal 引擎未装配");
      }
      return engine.newRun(
          com.aievolution.loop.temporal.HistoryTurn.fromMessages(history),
          MDC.get(TraceIdFilter.MDC_KEY),
          this.maxSteps,
          listener);
    }
    // W22 #1c：三角色管线引擎（Planner→Builder→Reviewer，Temporal activity 化）
    if ("pipeline".equals(this.loopEngine)) {
      com.aievolution.loop.temporal.TemporalPipelineEngine engine =
          this.temporalPipelineEngine.getIfAvailable();
      if (engine == null) {
        // 旗标与装配同键（TemporalPipelineConfig @ConditionalOnProperty），缺 Bean 即配置事故
        throw new IllegalStateException("ai.loop.engine=pipeline 但三角色管线引擎未装配");
      }
      return engine.newRun(
          com.aievolution.loop.temporal.HistoryTurn.fromMessages(history),
          MDC.get(TraceIdFilter.MDC_KEY),
          this.maxSteps,
          listener);
    }
    ResearchModel model =
        new DeepSeekResearchModel(
            chatClientBuilder,
            promptLibrary,
            toolRegistry.specs(),
            promptName,
            synthesisPromptName,
            parser,
            meterRegistry,
            history,
            skillRegistry,
            skillMode);
    return new ResearchLoop(model, toolRegistry, maxSteps, listener, traceSpans);
  }
}
