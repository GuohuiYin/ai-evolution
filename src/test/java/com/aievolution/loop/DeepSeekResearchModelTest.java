package com.aievolution.loop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aievolution.loop.protocol.TestProtocolMarkers;
import com.aievolution.prompt.PromptLibrary;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

/** W11 #3b-2：DeepSeek 适配——模型输出经 ReAct 协议解析为 ModelTurn；不合协议降级直通（原文即终答）。 */
class DeepSeekResearchModelTest {

  private ChatClient.Builder builder;
  private PromptLibrary promptLibrary;
  private ToolRegistry registry;
  private io.micrometer.core.instrument.simple.SimpleMeterRegistry meterRegistry;
  // 流式 API 测试手法：spec 自返回，链式调用收敛到同一 mock，stub 与 verify 都干净
  private ChatClient.ChatClientRequestSpec spec;
  private ChatClient.CallResponseSpec callSpec;

  @BeforeEach
  void setUp() {
    ChatClient chatClient = mock(ChatClient.class);
    spec = mock(ChatClient.ChatClientRequestSpec.class);
    callSpec = mock(ChatClient.CallResponseSpec.class);
    when(chatClient.prompt()).thenReturn(spec);
    when(spec.system(anyString())).thenReturn(spec);
    when(spec.user(anyString())).thenReturn(spec);
    when(spec.call()).thenReturn(callSpec);
    builder = mock(ChatClient.Builder.class);
    when(builder.build()).thenReturn(chatClient);
    meterRegistry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
    promptLibrary = mock(PromptLibrary.class);
    when(promptLibrary.exists("agent-react-v1")).thenReturn(true);
    when(promptLibrary.get("agent-react-v1")).thenReturn("协议说明\n{TOOL_MANUAL}");
    when(promptLibrary.exists("agent-react-synthesis-v1")).thenReturn(true);
    when(promptLibrary.get("agent-react-synthesis-v1")).thenReturn("合成规则：仅基于已查证信息");
    registry = mock(ToolRegistry.class);
    when(registry.specs())
        .thenReturn(
            List.of(new ToolRegistry.ToolSpec("getDailyQuotes", "查行情", "{\"code\":\"...\"}")));
  }

  private DeepSeekResearchModel model(List<Message> history) {
    return new DeepSeekResearchModel(
        builder,
        promptLibrary,
        registry,
        "agent-react-v1",
        "agent-react-synthesis-v1",
        TestProtocolMarkers.parser(),
        meterRegistry,
        history);
  }

  /** W22 #2：带技能注入的构造（三档模式） */
  private DeepSeekResearchModel modelWithSkill(
      com.aievolution.skill.SkillRegistry skillRegistry, String skillMode) {
    return new DeepSeekResearchModel(
        builder,
        promptLibrary,
        List.of(),
        "agent-react-v1",
        "agent-react-synthesis-v1",
        TestProtocolMarkers.parser(),
        meterRegistry,
        List.of(),
        skillRegistry,
        skillMode);
  }

  private static com.aievolution.skill.SkillRegistry dividendSkillRegistry() {
    return new com.aievolution.skill.SkillRegistry(
        List.of(
            new com.aievolution.skill.Skill(
                "dividend-caliber", "分红口径规程", List.of("分红"), "中期分红不计入年度预案口径")));
  }

  @Test
  void failsFastWhenPromptMissing() {
    when(promptLibrary.exists("nope")).thenReturn(false);
    assertThatThrownBy(
            () ->
                new DeepSeekResearchModel(
                    builder,
                    promptLibrary,
                    registry,
                    "nope",
                    "agent-react-synthesis-v1",
                    TestProtocolMarkers.parser(),
                    meterRegistry,
                    List.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("nope");
  }

  @Test
  void failsFastWhenSynthesisPromptMissing() {
    when(promptLibrary.exists("nope-synthesis")).thenReturn(false);
    assertThatThrownBy(
            () ->
                new DeepSeekResearchModel(
                    builder,
                    promptLibrary,
                    registry,
                    "agent-react-v1",
                    "nope-synthesis",
                    TestProtocolMarkers.parser(),
                    meterRegistry,
                    List.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("nope-synthesis");
  }

  @Test
  void rendersToolManualIntoSystemPrompt() {
    when(callSpec.content()).thenReturn("Thought: 够了\nFinal Answer: 答案");

    model(List.of()).nextTurn("问题", List.of());

    ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
    verify(spec).system(systemCaptor.capture());
    assertThat(systemCaptor.getValue()).contains("getDailyQuotes").contains("查行情");
  }

  @Test
  void transcriptAndConversationHistoryReachUserMessage() {
    when(callSpec.content())
        .thenReturn("Thought: 继续\nAction: getDailyQuotes\nAction Input: {\"code\":\"600519\"}");

    ModelTurn turn =
        model(List.of(new UserMessage("之前问过茅台")))
            .nextTurn("现在多少钱", List.of(new LoopStep(0, "先查行情", "getQuote", "600519", "1700.00")));

    assertThat(turn).isInstanceOf(ModelTurn.Act.class);
    ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
    verify(spec).user(userCaptor.capture());
    // 会话历史 + Loop 轨迹都要进上下文（多轮 + 多步的双重依据）
    assertThat(userCaptor.getValue()).contains("之前问过茅台").contains("现在多少钱").contains("1700.00");
  }

  @Test
  void unparseableOutputDegradesToDirectAnswer() {
    // 直通兜底：模型无视协议直接回答时，原文作为终答（不丢内容，协议遵从度靠 eval 度量）
    when(callSpec.content()).thenReturn("茅台的工艺是 12987");

    ModelTurn turn = model(List.of()).nextTurn("问题", List.of());

    assertThat(turn).isInstanceOf(ModelTurn.Final.class);
    assertThat(((ModelTurn.Final) turn).answer()).isEqualTo("茅台的工艺是 12987");
    // 协议违背必须可计量（W20 #1 子项 4）：WARN 之外加计数器，降解频率可观测可告警
    assertThat(meterRegistry.get("react.protocol.violation.total").counter().count())
        .isEqualTo(1.0);
  }

  @Test
  void compliantOutputDoesNotCountViolation() {
    when(callSpec.content()).thenReturn("Thought: 够了\nFinal Answer: 答案");

    model(List.of()).nextTurn("问题", List.of());

    assertThat(meterRegistry.find("react.protocol.violation.total").counter()).isNull();
  }

  @Test
  void synthesizeFeedsObservationsWithoutToolManual() {
    // W20 #1 裁决①B：合成步不挂工具手册，只喂问题 + 已查证 observations，强制收尾
    when(callSpec.content()).thenReturn("已查证：营收 100 亿（来源：mock，时点：2025-04-30）");
    List<LoopStep> steps =
        List.of(new LoopStep(0, "先查行情", "getDailyQuotes", "{\"code\":\"600519\"}", "1700.00"));

    java.util.Optional<String> answer = model(List.of()).synthesize("茅台营收多少", steps);

    assertThat(answer).contains("已查证：营收 100 亿（来源：mock，时点：2025-04-30）");
    ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
    verify(spec).system(systemCaptor.capture());
    verify(spec).user(userCaptor.capture());
    assertThat(systemCaptor.getValue()).contains("仅基于已查证信息").doesNotContain("{TOOL_MANUAL}");
    assertThat(userCaptor.getValue()).contains("茅台营收多少").contains("1700.00");
  }

  @Test
  void synthesizeBlankDegradesToFallback() {
    // 合成失败（空内容）→ 空，调用方退兜底文案（裁决①B：诚实红线优先于硬凑答案）
    when(callSpec.content()).thenReturn("  ");

    assertThat(model(List.of()).synthesize("问题", List.of())).isEmpty();
  }

  @Test
  void offModeInjectsNoSkillContent() {
    when(callSpec.content()).thenReturn("Thought: 够了\nFinal Answer: 答案");

    modelWithSkill(dividendSkillRegistry(), "off").nextTurn("分红多少", List.of());

    ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
    verify(spec).system(systemCaptor.capture());
    verify(spec).user(userCaptor.capture());
    assertThat(systemCaptor.getValue()).doesNotContain("技能").doesNotContain("中期分红");
    assertThat(userCaptor.getValue()).doesNotContain("中期分红");
  }

  @Test
  void fullModeKeepsAllBodiesInSystemPrompt() {
    // 对照臂语义：静态全量注入——正文常驻系统 prompt，当轮消息不带
    when(callSpec.content()).thenReturn("Thought: 够了\nFinal Answer: 答案");

    modelWithSkill(dividendSkillRegistry(), "full").nextTurn("分红多少", List.of());

    ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
    verify(spec).system(systemCaptor.capture());
    verify(spec).user(userCaptor.capture());
    assertThat(systemCaptor.getValue()).contains("【技能规程】").contains("中期分红不计入年度预案口径");
    assertThat(userCaptor.getValue()).doesNotContain("中期分红不计入年度预案口径");
  }

  @Test
  void progressiveModeMetadataInSystemMatchedBodyInUserMessage() {
    // 渐进披露：元数据常驻系统 prompt；命中触发的正文注入当轮 user 消息
    when(callSpec.content()).thenReturn("Thought: 够了\nFinal Answer: 答案");

    modelWithSkill(dividendSkillRegistry(), "progressive").nextTurn("分红多少", List.of());

    ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
    verify(spec).system(systemCaptor.capture());
    verify(spec).user(userCaptor.capture());
    assertThat(systemCaptor.getValue())
        .contains("【可用技能】")
        .contains("- dividend-caliber：分红口径规程")
        .doesNotContain("中期分红不计入年度预案口径");
    assertThat(userCaptor.getValue()).contains("【命中技能规程】").contains("中期分红不计入年度预案口径");
  }

  @Test
  void progressiveModeUnmatchedQuestionGetsNoBody() {
    // 未命中：正文不进当轮消息——token 节省的全部来源
    when(callSpec.content()).thenReturn("Thought: 够了\nFinal Answer: 答案");

    modelWithSkill(dividendSkillRegistry(), "progressive").nextTurn("营收多少", List.of());

    ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
    verify(spec).user(userCaptor.capture());
    assertThat(userCaptor.getValue()).doesNotContain("命中技能规程").doesNotContain("中期分红");
  }

  @Test
  void progressiveModeInjectsIntoSynthesisMessage() {
    // 合成步是措辞定稿处——命中规程同样注入（口径错误多发于收尾）
    when(callSpec.content()).thenReturn("合成答案");

    modelWithSkill(dividendSkillRegistry(), "progressive").synthesize("中期分红算在里面吗", List.of());

    ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
    verify(spec).user(userCaptor.capture());
    assertThat(userCaptor.getValue()).contains("【命中技能规程】").contains("中期分红不计入年度预案口径");
  }

  @Test
  void unknownSkillModeFailsFast() {
    // 模式拼写错误是配置事故：fail-fast，不静默退化成 off（机制悄悄失效最难排查）
    assertThatThrownBy(() -> modelWithSkill(dividendSkillRegistry(), "progessive"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("progessive");
  }
}
