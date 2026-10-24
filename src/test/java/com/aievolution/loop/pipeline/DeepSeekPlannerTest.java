package com.aievolution.loop.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aievolution.prompt.PromptLibrary;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;

/** W22 #1b：Planner 组件——模型输出严格 JSON 计划解析为 {@link Plan}； 不可解析降级为单子问题保底（不阻塞链路，设计 §1.3 失败语义）。 */
class DeepSeekPlannerTest {

  private ChatClient.ChatClientRequestSpec spec;
  private ChatClient.CallResponseSpec callSpec;
  private PromptLibrary promptLibrary;
  private SimpleMeterRegistry meterRegistry;
  private ChatClient.Builder builder;

  @BeforeEach
  void setUp() {
    // 流式 API 测试手法（DeepSeekResearchModelTest 先例）：spec 自返回，链式收敛到同一 mock
    ChatClient chatClient = mock(ChatClient.class);
    spec = mock(ChatClient.ChatClientRequestSpec.class);
    callSpec = mock(ChatClient.CallResponseSpec.class);
    when(chatClient.prompt()).thenReturn(spec);
    when(spec.user(anyString())).thenReturn(spec);
    when(spec.call()).thenReturn(callSpec);
    builder = mock(ChatClient.Builder.class);
    when(builder.build()).thenReturn(chatClient);
    meterRegistry = new SimpleMeterRegistry();
    promptLibrary = mock(PromptLibrary.class);
    when(promptLibrary.exists("planner-v1")).thenReturn(true);
    when(promptLibrary.get("planner-v1")).thenReturn("规划器规则\n【会话历史】\n{history}\n【用户问题】\n{query}");
  }

  private DeepSeekPlanner planner() {
    return new DeepSeekPlanner(builder, promptLibrary, "planner-v1", meterRegistry);
  }

  @Test
  void failsFastWhenPromptMissing() {
    when(promptLibrary.exists("nope")).thenReturn(false);
    assertThatThrownBy(() -> new DeepSeekPlanner(builder, promptLibrary, "nope", meterRegistry))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("nope");
  }

  @Test
  void parsesStrictJsonPlan() {
    when(callSpec.content())
        .thenReturn("{\"subQuestions\":[{\"q\":\"贵州茅台2025年归母净利润是多少\",\"channel\":\"rag\"}]}");

    Plan plan = planner().plan("茅台 2025 年归母净利润是多少", List.of());

    assertThat(plan.subQuestions())
        .containsExactly(new Plan.SubQuestion("贵州茅台2025年归母净利润是多少", Channel.RAG));
    assertThat(meterRegistry.find("planner.parse.fallback.total").counter()).isNull();
  }

  @Test
  void parsesCompoundPlanWithMultipleChannels() {
    when(callSpec.content())
        .thenReturn(
            "{\"subQuestions\":[{\"q\":\"贵州茅台2024年净利润是多少\",\"channel\":\"rag\"},"
                + "{\"q\":\"贵州茅台最新股价\",\"channel\":\"stock\"}]}");

    Plan plan = planner().plan("茅台净利和最新股价", List.of());

    assertThat(plan.subQuestions())
        .containsExactly(
            new Plan.SubQuestion("贵州茅台2024年净利润是多少", Channel.RAG),
            new Plan.SubQuestion("贵州茅台最新股价", Channel.STOCK));
  }

  @Test
  void toleratesLeadingWhitespaceAndCodeFence() {
    // Qwen 臂实测输出带前导空行；markdown 代码围栏也是常见包裹——提取首个 { 到末个 } 容错
    when(callSpec.content())
        .thenReturn(
            "\n\n```json\n{\"subQuestions\":[{\"q\":\"x\",\"channel\":\"announcement\"}]}\n```");

    Plan plan = planner().plan("问题", List.of());

    assertThat(plan.subQuestions())
        .containsExactly(new Plan.SubQuestion("x", Channel.ANNOUNCEMENT));
  }

  @Test
  void historyAndQuestionRenderedIntoUserMessage() {
    // 与实验脚本口径一致：整段渲染后作为单条 user 消息（无 system）
    when(callSpec.content()).thenReturn("{\"subQuestions\":[{\"q\":\"x\",\"channel\":\"none\"}]}");

    planner()
        .plan(
            "中期分红算在里面吗",
            List.of(new UserMessage("茅台 2024 年度分红比例是多少"), new AssistantMessage("约 75%")));

    ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
    verify(spec).user(userCaptor.capture());
    assertThat(userCaptor.getValue())
        .contains("茅台 2024 年度分红比例是多少")
        .contains("约 75%")
        .contains("中期分红算在里面吗")
        .doesNotContain("{history}")
        .doesNotContain("{query}");
  }

  @Test
  void unparseableOutputFallsBackToSingleRagQuestion() {
    when(callSpec.content()).thenReturn("我不知道怎么拆");

    Plan plan = planner().plan("茅台营收多少", List.of());

    assertThat(plan.subQuestions()).containsExactly(new Plan.SubQuestion("茅台营收多少", Channel.RAG));
    // 降级必须可计量（react.protocol.violation.total 同款纪律）
    assertThat(meterRegistry.get("planner.parse.fallback.total").counter().count()).isEqualTo(1.0);
  }

  @Test
  void blankOutputFallsBack() {
    when(callSpec.content()).thenReturn("  ");

    Plan plan = planner().plan("问题", List.of());

    assertThat(plan.subQuestions()).hasSize(1);
    assertThat(plan.subQuestions().getFirst().channel()).isEqualTo(Channel.RAG);
    assertThat(meterRegistry.get("planner.parse.fallback.total").counter().count()).isEqualTo(1.0);
  }

  @Test
  void emptySubQuestionsFallsBack() {
    when(callSpec.content()).thenReturn("{\"subQuestions\":[]}");

    Plan plan = planner().plan("问题", List.of());

    assertThat(plan.subQuestions()).hasSize(1);
    assertThat(meterRegistry.get("planner.parse.fallback.total").counter().count()).isEqualTo(1.0);
  }

  @Test
  void unknownChannelFallsBack() {
    // 通路枚举外的值（如模型编造 "web"）视同计划不可解析——严格好于猜
    when(callSpec.content()).thenReturn("{\"subQuestions\":[{\"q\":\"x\",\"channel\":\"web\"}]}");

    Plan plan = planner().plan("问题", List.of());

    assertThat(plan.subQuestions()).containsExactly(new Plan.SubQuestion("问题", Channel.RAG));
    assertThat(meterRegistry.get("planner.parse.fallback.total").counter().count()).isEqualTo(1.0);
  }

  @Test
  void blankSubQuestionDroppedAndMissingChannelDefaultsToRag() {
    when(callSpec.content())
        .thenReturn(
            "{\"subQuestions\":[{\"q\":\"  \",\"channel\":\"rag\"}," + "{\"q\":\"有效问题\"}]}");

    Plan plan = planner().plan("问题", List.of());

    assertThat(plan.subQuestions()).containsExactly(new Plan.SubQuestion("有效问题", Channel.RAG));
    assertThat(meterRegistry.find("planner.parse.fallback.total").counter()).isNull();
  }
}
