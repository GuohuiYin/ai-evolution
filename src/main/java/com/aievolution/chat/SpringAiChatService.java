package com.aievolution.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/** 基于 Spring AI ChatClient 的 {@link ChatService} 实现，模型供应商由配置决定（当前为 DeepSeek）。 */
@Service
public class SpringAiChatService implements ChatService {

  private final ChatClient chatClient;

  public SpringAiChatService(ChatClient.Builder chatClientBuilder) {
    this.chatClient = chatClientBuilder.build();
  }

  @Override
  public String chat(String message) {
    return chatClient.prompt().user(message).call().content();
  }
}
