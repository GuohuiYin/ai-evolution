package com.aievolution.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

class SpringAiChatServiceTest {

  @Test
  void delegatesToChatClientAndReturnsContent() {
    ChatClient.Builder builder = mock(ChatClient.Builder.class);
    ChatClient chatClient = mock(ChatClient.class);
    ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
    ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);

    when(builder.build()).thenReturn(chatClient);
    when(chatClient.prompt()).thenReturn(requestSpec);
    when(requestSpec.user("市盈率是什么")).thenReturn(requestSpec);
    when(requestSpec.call()).thenReturn(callSpec);
    when(callSpec.content()).thenReturn("市盈率是股价与每股收益的比率");

    ChatService service = new SpringAiChatService(builder);

    assertThat(service.chat("市盈率是什么")).isEqualTo("市盈率是股价与每股收益的比率");
  }
}
