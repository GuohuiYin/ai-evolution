package com.aievolution.loop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    promptLibrary = mock(PromptLibrary.class);
    when(promptLibrary.exists("agent-react-v1")).thenReturn(true);
    when(promptLibrary.get("agent-react-v1")).thenReturn("协议说明\n{TOOL_MANUAL}");
    registry = mock(ToolRegistry.class);
    when(registry.specs())
        .thenReturn(
            List.of(new ToolRegistry.ToolSpec("getDailyQuotes", "查行情", "{\"code\":\"...\"}")));
  }

  private DeepSeekResearchModel model(List<Message> history) {
    return new DeepSeekResearchModel(
        builder, promptLibrary, registry, "agent-react-v1", new ReactProtocolParser(), history);
  }

  @Test
  void failsFastWhenPromptMissing() {
    when(promptLibrary.exists("nope")).thenReturn(false);
    assertThatThrownBy(
            () ->
                new DeepSeekResearchModel(
                    builder, promptLibrary, registry, "nope", new ReactProtocolParser(), List.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("nope");
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
  }
}
