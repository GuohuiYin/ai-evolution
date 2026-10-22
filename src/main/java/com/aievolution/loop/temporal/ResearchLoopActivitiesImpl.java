package com.aievolution.loop.temporal;

import com.aievolution.infra.LogSummaries;
import com.aievolution.infra.TraceIdFilter;
import com.aievolution.loop.DeepSeekResearchModel;
import com.aievolution.loop.LoopStep;
import com.aievolution.loop.ReactProtocolParser;
import com.aievolution.loop.ResearchModel;
import com.aievolution.loop.ToolRegistry;
import com.aievolution.prompt.PromptLibrary;
import io.micrometer.core.instrument.MeterRegistry;
import io.temporal.activity.Activity;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;

/**
 * {@link ResearchLoopActivities} 实现（W21 #1②）：worker 常驻单例，app 级依赖
 * （ChatClient.Builder/prompt/parser/工具/计数器）构造注入；每请求差异（会话历史/traceId/ listener）全部走 activity 入参与
 * {@link LoopListenerRegistry} 桥——模型适配对象 每次调用现场构建（廉价纯构造），不跨请求共享。
 */
public class ResearchLoopActivitiesImpl implements ResearchLoopActivities {

  private static final Logger traceLog = LoggerFactory.getLogger("research-trace");

  private final ChatClient.Builder chatClientBuilder;
  private final PromptLibrary promptLibrary;
  private final ToolRegistry toolRegistry;
  private final String promptName;
  private final String synthesisPromptName;
  private final ReactProtocolParser parser;
  private final MeterRegistry meterRegistry;
  private final LoopListenerRegistry listenerRegistry;

  public ResearchLoopActivitiesImpl(
      ChatClient.Builder chatClientBuilder,
      PromptLibrary promptLibrary,
      ToolRegistry toolRegistry,
      String promptName,
      String synthesisPromptName,
      ReactProtocolParser parser,
      MeterRegistry meterRegistry,
      LoopListenerRegistry listenerRegistry) {
    this.chatClientBuilder = chatClientBuilder;
    this.promptLibrary = promptLibrary;
    this.toolRegistry = toolRegistry;
    this.promptName = promptName;
    this.synthesisPromptName = synthesisPromptName;
    this.parser = parser;
    this.meterRegistry = meterRegistry;
    this.listenerRegistry = listenerRegistry;
  }

  @Override
  public ModelTurnPayload nextTurn(
      String question, List<LoopStep> steps, List<HistoryTurn> history, String traceId) {
    try {
      setTraceId(traceId);
      return ModelTurnPayload.from(newModel(history).nextTurn(question, steps));
    } finally {
      clearTraceId();
    }
  }

  @Override
  public String synthesize(
      String question, List<LoopStep> steps, List<HistoryTurn> history, String traceId) {
    try {
      setTraceId(traceId);
      // 空串 = 合成失败哨兵（null 不跨 payload 边界）
      return newModel(history).synthesize(question, steps).orElse("");
    } finally {
      clearTraceId();
    }
  }

  @Override
  public LoopStep executeStep(
      int index, String thought, String tool, String input, String traceId) {
    try {
      setTraceId(traceId);
      // 与 inline 引擎 ResearchLoop.executeStep 同格式留痕（单调时钟计时先例）
      long startNanos = System.nanoTime();
      String observation = this.toolRegistry.execute(tool, input);
      LoopStep step = new LoopStep(index, thought, tool, input, observation);
      traceLog.info(
          "step={} thought={} tool={} input={} observation={} elapsedMs={} engine=temporal",
          index,
          LogSummaries.summarize(thought),
          tool,
          LogSummaries.summarize(input),
          LogSummaries.summarize(observation),
          (System.nanoTime() - startNanos) / 1_000_000);
      // 逐步轨迹推送：workflowId 查回当前请求的 listener（SSE 与 inline 同行为）
      this.listenerRegistry
          .get(Activity.getExecutionContext().getInfo().getWorkflowId())
          .onStep(step);
      return step;
    } finally {
      clearTraceId();
    }
  }

  private ResearchModel newModel(List<HistoryTurn> history) {
    List<Message> messages = history.stream().map(HistoryTurn::toMessage).toList();
    return new DeepSeekResearchModel(
        this.chatClientBuilder,
        this.promptLibrary,
        this.toolRegistry,
        this.promptName,
        this.synthesisPromptName,
        this.parser,
        this.meterRegistry,
        messages);
  }

  // MDC 不跨线程：activity 在 worker 线程执行，请求侧 traceId 经入参手工交接，日志不断链
  private static void setTraceId(String traceId) {
    if (traceId != null && !traceId.isBlank()) {
      MDC.put(TraceIdFilter.MDC_KEY, traceId);
    }
  }

  private static void clearTraceId() {
    MDC.remove(TraceIdFilter.MDC_KEY);
  }
}
