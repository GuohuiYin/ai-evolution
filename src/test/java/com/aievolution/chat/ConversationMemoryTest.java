package com.aievolution.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aievolution.prompt.ClasspathPromptLibrary;
import com.aievolution.rag.KnowledgeRetriever;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;

/** 会话记忆行为测试（W10 #0a-2）：真实 advisor + 内存仓储，mock 模型——验证的是记忆内容，不是协作。 */
class ConversationMemoryTest {

  private ChatModel chatModel;
  private ArgumentCaptor<Prompt> promptCaptor;
  private RagChatService service;

  @BeforeEach
  void setUp() {
    chatModel = mock(ChatModel.class);
    promptCaptor = ArgumentCaptor.forClass(Prompt.class);
    // DeepSeek 风格 ChatModel 契约：ChatClient 构建时会读默认 Options
    when(chatModel.getDefaultOptions())
        .thenReturn(org.springframework.ai.chat.prompt.ChatOptions.builder().build());
    when(chatModel.getOptions())
        .thenReturn(org.springframework.ai.chat.prompt.ChatOptions.builder().build());
    when(chatModel.call(promptCaptor.capture()))
        .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("第一轮回答")))));

    KnowledgeRetriever retriever = mock(KnowledgeRetriever.class);
    when(retriever.retrieve(any()))
        .thenReturn(List.of(new Document("id1", "知识片段", Map.of("source", "maotai.md"))));

    ChatMemory memory =
        MessageWindowChatMemory.builder()
            .chatMemoryRepository(new InMemoryChatMemoryRepository())
            .maxMessages(10)
            .build();
    service =
        new RagChatService(
            ChatClient.builder(chatModel),
            retriever,
            new ClasspathPromptLibrary(),
            "rag-chat-v1",
            memory);
  }

  @Test
  void followUpInSameConversationSeesPreviousTurn() {
    service.chat("茅台的酿造工艺是什么", "conv-1");
    service.chat("它呢", "conv-1"); // 追问：第二轮 prompt 必须带上一轮问答

    List<Prompt> prompts = promptCaptor.getAllValues();
    String secondTurn = prompts.get(1).getContents();
    assertThat(secondTurn).contains("茅台的酿造工艺是什么").contains("第一轮回答");
  }

  @Test
  void separateConversationsAreIsolated() {
    service.chat("茅台的酿造工艺是什么", "conv-1");
    service.chat("特斯拉怎么样", "conv-2"); // 新会话：不得看到 conv-1 的内容

    List<Prompt> prompts = promptCaptor.getAllValues();
    assertThat(prompts.get(1).getContents()).doesNotContain("茅台的酿造工艺是什么");
  }
}
