package com.aievolution.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aievolution.loop.ReactProtocolParser;
import com.aievolution.loop.ToolRegistry;
import com.aievolution.prompt.ClasspathPromptLibrary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;

/** W11 #3c：AgentChatService 由显式 ResearchLoop 驱动——记忆手工读写、超限兜底、免责声明收尾。 */
class AgentChatServiceTest {

  private ChatClient.Builder builder;
  private ChatClient.CallResponseSpec callSpec;
  private ChatClient.ChatClientRequestSpec spec;
  private ToolRegistry registry;
  private final ChatMemory chatMemory =
      MessageWindowChatMemory.builder()
          .chatMemoryRepository(new InMemoryChatMemoryRepository())
          .maxMessages(10)
          .build();

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
    registry = mock(ToolRegistry.class);
    when(registry.specs()).thenReturn(java.util.List.of());
  }

  private AgentChatService service(String promptName, int maxSteps) {
    return new AgentChatService(
        builder,
        registry,
        new ClasspathPromptLibrary(),
        new ReactProtocolParser(),
        chatMemory,
        promptName,
        maxSteps);
  }

  @Test
  void failsFastWhenPromptTemplateMissing() {
    // prompt 缺失是部署事故，启动即 fail-fast（与 RagChatService 同模式）
    assertThatThrownBy(() -> service("agent-react-not-exist", 6))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("agent-react-not-exist");
  }

  @Test
  void constructsWhenPromptTemplateExists() {
    assertThatCode(() -> service("agent-react-v1", 6)).doesNotThrowAnyException();
  }

  @Test
  void chatRunsLoopAndWritesMemory() {
    when(callSpec.content()).thenReturn("Thought: 直接答\nFinal Answer: 茅台收盘价 1700 元");

    ChatAnswer answer = service("agent-react-v1", 6).chat("茅台多少钱", "conv-t1");

    assertThat(answer.reply()).contains("茅台收盘价 1700 元").contains("不构成投资建议");
    // 记忆手工写回：本轮问答入窗口（原 advisor 会在循环每步重复追加，故改手工）
    assertThat(chatMemory.get("conv-t1"))
        .hasSize(2)
        .satisfies(
            msgs -> {
              assertThat(msgs.get(0)).isInstanceOf(UserMessage.class);
              assertThat(msgs.get(1)).isInstanceOf(AssistantMessage.class);
            });
  }

  @Test
  void chatFallsBackWhenMaxStepsExhausted() {
    when(callSpec.content())
        .thenReturn("Thought: 继续查\nAction: getDailyQuotes\nAction Input: {\"code\":\"600519\"}");
    when(registry.execute(anyString(), anyString())).thenReturn("观察");

    ChatAnswer answer = service("agent-react-v1", 2).chat("追不完的问题", "conv-t2");

    // 超限不编造：兜底话术 + 轨迹已留痕（不击穿、不假装收敛）
    assertThat(answer.reply()).contains("未能在限定的 2 步内得出结论");
    verify(registry, times(2)).execute(anyString(), anyString());
  }
}
