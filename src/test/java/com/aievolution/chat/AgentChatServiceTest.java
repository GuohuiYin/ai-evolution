package com.aievolution.chat;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aievolution.prompt.ClasspathPromptLibrary;
import com.aievolution.tool.AnnouncementTools;
import com.aievolution.tool.StockDataTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;

class AgentChatServiceTest {

  private ChatClient.Builder builder;
  private final ChatMemory chatMemory =
      MessageWindowChatMemory.builder()
          .chatMemoryRepository(new InMemoryChatMemoryRepository())
          .maxMessages(10)
          .build();

  @BeforeEach
  void setUp() {
    builder = mock(ChatClient.Builder.class);
    when(builder.defaultAdvisors(any(Advisor.class))).thenReturn(builder);
    when(builder.build()).thenReturn(mock(ChatClient.class));
  }

  @Test
  void failsFastWhenPromptTemplateMissing() {
    // prompt 缺失是部署事故，启动即 fail-fast（与 RagChatService 同模式）
    assertThatThrownBy(
            () ->
                new AgentChatService(
                    builder,
                    mock(StockDataTools.class),
                    mock(AnnouncementTools.class),
                    new ClasspathPromptLibrary(),
                    "agent-chat-not-exist",
                    chatMemory))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("agent-chat-not-exist");
  }

  @Test
  void constructsWhenPromptTemplateExists() {
    assertThatCode(
            () ->
                new AgentChatService(
                    builder,
                    mock(StockDataTools.class),
                    mock(AnnouncementTools.class),
                    new ClasspathPromptLibrary(),
                    "agent-chat-v2",
                    chatMemory))
        .doesNotThrowAnyException();
  }
}
