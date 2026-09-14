package com.aievolution.chat;

import com.aievolution.compliance.Disclaimers;
import com.aievolution.infra.TraceIdFilter;
import com.aievolution.loop.DeepSeekResearchModel;
import com.aievolution.loop.LoopListener;
import com.aievolution.loop.LoopResult;
import com.aievolution.loop.LoopStep;
import com.aievolution.loop.ReactProtocolParser;
import com.aievolution.loop.ResearchLoop;
import com.aievolution.loop.ResearchModel;
import com.aievolution.loop.ToolRegistry;
import com.aievolution.prompt.PromptLibrary;
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
  private final String promptName;
  private final int maxSteps;

  public AgentChatService(
      ChatClient.Builder chatClientBuilder,
      ToolRegistry toolRegistry,
      PromptLibrary promptLibrary,
      ReactProtocolParser parser,
      ChatMemory chatMemory,
      @Value("${ai.loop.react-prompt:agent-react-v1}") String promptName,
      @Value("${ai.loop.max-steps:6}") int maxSteps) {
    if (!promptLibrary.exists(promptName)) {
      // prompt 缺失是部署事故，启动即 fail-fast
      throw new IllegalStateException("ReAct prompt 模板不存在: " + promptName);
    }
    this.chatClientBuilder = chatClientBuilder;
    this.toolRegistry = toolRegistry;
    this.promptLibrary = promptLibrary;
    this.parser = parser;
    this.chatMemory = chatMemory;
    this.promptName = promptName;
    this.maxSteps = maxSteps;
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
          try {
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

  /** 超限兜底话术：不编造结论，如实告知并指向已留痕的轨迹（#1 决策：红线措辞归 chat 域） */
  private String answerOf(LoopResult result) {
    if (result.answer() != null) {
      return result.answer();
    }
    return "本次研究未能在限定的 %d 步内得出结论。已完成的查证步骤可在研究轨迹中查看；建议缩小问题范围后重试。".formatted(maxSteps);
  }

  private ResearchLoop newLoop(String conversationId, LoopListener listener) {
    List<Message> history = chatMemory.get(conversationId);
    ResearchModel model =
        new DeepSeekResearchModel(
            chatClientBuilder, promptLibrary, toolRegistry, promptName, parser, history);
    return new ResearchLoop(model, toolRegistry, maxSteps, listener);
  }
}
