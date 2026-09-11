package com.aievolution.infra;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 会话记忆原语（W10 #0a-2）：内存版 L1——{@link MessageWindowChatMemory} + 滑动窗口， 会话身份经 {@code
 * ChatMemory.CONVERSATION_ID} 参数按请求隔离。
 *
 * <p>刻意不做全局挂载：分析服务与 eval judge 也用 ChatClient，共享会话记忆会互相污染；
 * 记忆只由对话域（RagChatService/AgentChatService）在构造器定点挂载。
 *
 * <p>已知边界（五级路线见 docs/plans/m3/README.md）：内存仓储重启即失忆、K8s 多副本断片； L2 持久化（Redis/DB）挂 W14+。
 */
@Configuration
public class ConversationMemoryConfig {

  @Bean
  public ChatMemory chatMemory(@Value("${ai.chat.memory.window:20}") int window) {
    return MessageWindowChatMemory.builder()
        .chatMemoryRepository(new InMemoryChatMemoryRepository())
        .maxMessages(window)
        .build();
  }
}
