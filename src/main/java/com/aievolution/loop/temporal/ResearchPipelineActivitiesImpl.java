package com.aievolution.loop.temporal;

import com.aievolution.infra.TraceIdFilter;
import com.aievolution.loop.DeepSeekResearchModel;
import com.aievolution.loop.LoopListener;
import com.aievolution.loop.LoopResult;
import com.aievolution.loop.LoopStep;
import com.aievolution.loop.ReactProtocolParser;
import com.aievolution.loop.ResearchLoop;
import com.aievolution.loop.ToolRegistry;
import com.aievolution.loop.pipeline.Channel;
import com.aievolution.loop.pipeline.ChannelToolExecutor;
import com.aievolution.loop.pipeline.DeepSeekPlanner;
import com.aievolution.loop.pipeline.DeepSeekReviewer;
import com.aievolution.loop.pipeline.DraftAnswer;
import com.aievolution.loop.pipeline.EvidencePack;
import com.aievolution.loop.pipeline.Plan;
import com.aievolution.loop.pipeline.ReviewVerdict;
import com.aievolution.prompt.PromptLibrary;
import io.micrometer.core.instrument.MeterRegistry;
import io.temporal.activity.Activity;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;

/**
 * {@link ResearchPipelineActivities} 实现（W22 #1c）：worker 常驻单例， app
 * 级依赖构造注入，每请求差异（历史/traceId/listener）走入参与 {@link LoopListenerRegistry} 桥——与 {@link
 * ResearchLoopActivitiesImpl} 同款结构。
 *
 * <p>Builder 收窄语义（设计 §1.3）：按计划逐子问题跑 ReAct 取证循环， 工具面板经 {@link ChannelToolExecutor} 按通路白名单收窄（prompt
 * 手册与执行白名单同源）； 起草复用合成 prompt（无工具手册，裁决①B）；打回批注以【上轮复核批注】段回流重写。
 */
public class ResearchPipelineActivitiesImpl implements ResearchPipelineActivities {

  private final ChatClient.Builder chatClientBuilder;
  private final PromptLibrary promptLibrary;
  private final ToolRegistry toolRegistry;
  private final String plannerPromptName;
  private final String reactPromptName;
  private final String synthesisPromptName;
  private final String reviewerPromptName;
  private final ReactProtocolParser parser;
  private final MeterRegistry meterRegistry;
  private final LoopListenerRegistry listenerRegistry;

  public ResearchPipelineActivitiesImpl(
      ChatClient.Builder chatClientBuilder,
      PromptLibrary promptLibrary,
      ToolRegistry toolRegistry,
      String plannerPromptName,
      String reactPromptName,
      String synthesisPromptName,
      String reviewerPromptName,
      ReactProtocolParser parser,
      MeterRegistry meterRegistry,
      LoopListenerRegistry listenerRegistry) {
    this.chatClientBuilder = chatClientBuilder;
    this.promptLibrary = promptLibrary;
    this.toolRegistry = toolRegistry;
    this.plannerPromptName = plannerPromptName;
    this.reactPromptName = reactPromptName;
    this.synthesisPromptName = synthesisPromptName;
    this.reviewerPromptName = reviewerPromptName;
    this.parser = parser;
    this.meterRegistry = meterRegistry;
    this.listenerRegistry = listenerRegistry;
  }

  @Override
  public Plan plan(String question, List<HistoryTurn> history, String traceId) {
    try {
      setTraceId(traceId);
      return new DeepSeekPlanner(
              this.chatClientBuilder,
              this.promptLibrary,
              this.plannerPromptName,
              this.meterRegistry)
          .plan(question, messages(history));
    } finally {
      clearTraceId();
    }
  }

  @Override
  public BuildOutcome build(
      String question,
      Plan plan,
      List<HistoryTurn> history,
      String reviewComment,
      int maxSteps,
      String traceId) {
    try {
      setTraceId(traceId);
      List<Message> messages = messages(history);
      // 逐步轨迹推送同单循环引擎；onComplete 由引擎层统一收尾（子循环不单独收尾）
      LoopListener listener = forwardStepsOnly(workflowListener());
      List<LoopStep> allSteps = new ArrayList<>();
      for (Plan.SubQuestion sq : plan.subQuestions()) {
        if (sq.channel() == Channel.NONE) {
          // 红线类：不取证（设计 §1.3），草稿由起草步出拒答措辞
          continue;
        }
        DeepSeekResearchModel model =
            new DeepSeekResearchModel(
                this.chatClientBuilder,
                this.promptLibrary,
                ChannelToolExecutor.filteredSpecs(this.toolRegistry.specs(), sq.channel()),
                this.reactPromptName,
                this.synthesisPromptName,
                this.parser,
                this.meterRegistry,
                messages);
        ResearchLoop loop =
            new ResearchLoop(
                model,
                new ChannelToolExecutor(this.toolRegistry, sq.channel()),
                maxSteps,
                listener);
        LoopResult result = loop.run(sq.q());
        for (LoopStep s : result.steps()) {
          // 步号跨子问题全局重排（轨迹与 LoopResult 一致）
          allSteps.add(
              new LoopStep(allSteps.size(), s.thought(), s.tool(), s.input(), s.observation()));
        }
      }
      // 起草：打回批注回流（设计 §1.3：批注供 Builder 重写时修正）
      String draftQuestion =
          reviewComment == null || reviewComment.isBlank()
              ? question
              : question + "\n\n【上轮复核批注】\n" + reviewComment;
      DeepSeekResearchModel synthesizer =
          new DeepSeekResearchModel(
              this.chatClientBuilder,
              this.promptLibrary,
              this.toolRegistry,
              this.reactPromptName,
              this.synthesisPromptName,
              this.parser,
              this.meterRegistry,
              messages);
      // 合成失败 = null 哨兵（诚实红线：workflow 层 answer=null 收尾，不送复核）
      String draft = synthesizer.synthesize(draftQuestion, List.copyOf(allSteps)).orElse(null);
      return new BuildOutcome(List.copyOf(allSteps), draft);
    } finally {
      clearTraceId();
    }
  }

  @Override
  public ReviewVerdict review(String question, BuildOutcome outcome, String traceId) {
    try {
      setTraceId(traceId);
      return new DeepSeekReviewer(
              this.chatClientBuilder,
              this.promptLibrary,
              this.reviewerPromptName,
              this.meterRegistry)
          .review(
              question, EvidencePack.fromSteps(outcome.steps()), new DraftAnswer(outcome.draft()));
    } finally {
      clearTraceId();
    }
  }

  /** 子循环只转发 onStep（SSE 轨迹不断流）；onComplete 由引擎层对整条管线统一收尾 */
  private static LoopListener forwardStepsOnly(LoopListener listener) {
    return new LoopListener() {
      @Override
      public void onStep(LoopStep step) {
        listener.onStep(step);
      }
    };
  }

  private LoopListener workflowListener() {
    return this.listenerRegistry.get(Activity.getExecutionContext().getInfo().getWorkflowId());
  }

  private static List<Message> messages(List<HistoryTurn> history) {
    return history.stream().map(HistoryTurn::toMessage).toList();
  }

  // MDC 不跨线程：activity 在 worker 线程执行，请求侧 traceId 经入参手工交接（ResearchLoopActivitiesImpl 先例）
  private static void setTraceId(String traceId) {
    if (traceId != null && !traceId.isBlank()) {
      MDC.put(TraceIdFilter.MDC_KEY, traceId);
    }
  }

  private static void clearTraceId() {
    MDC.remove(TraceIdFilter.MDC_KEY);
  }
}
