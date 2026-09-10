package com.aievolution.chat;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aievolution.prompt.ClasspathPromptLibrary;
import com.aievolution.tool.AnnouncementTools;
import com.aievolution.tool.StockDataTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

class AgentChatServiceTest {

  private ChatClient.Builder builder;

  @BeforeEach
  void setUp() {
    builder = mock(ChatClient.Builder.class);
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
                    "agent-chat-not-exist"))
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
                    "agent-chat-v2"))
        .doesNotThrowAnyException();
  }
}
