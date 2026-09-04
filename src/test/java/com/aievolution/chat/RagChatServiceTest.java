package com.aievolution.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

class RagChatServiceTest {

  private ChatClient chatClient;
  private ChatClient.ChatClientRequestSpec requestSpec;
  private VectorStore vectorStore;
  private RagChatService service;

  @BeforeEach
  void setUp() {
    ChatClient.Builder builder = mock(ChatClient.Builder.class);
    chatClient = mock(ChatClient.class);
    requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
    ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
    vectorStore = mock(VectorStore.class);

    when(builder.build()).thenReturn(chatClient);
    when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
    when(requestSpec.call()).thenReturn(callSpec);
    when(callSpec.content()).thenReturn("模型回复");

    service = new RagChatService(builder, vectorStore, new PromptLibrary(), 0.5);
  }

  @Test
  void answersWithSourcesAndDisclaimerWhenKnowledgeFound() {
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(
            List.of(
                new Document("id1", "营业总收入约1740亿元（数据时点：2024年年报）", Map.of("source", "maotai.md"))));

    ChatAnswer answer = service.chat("茅台营收多少？");

    assertThat(answer.reply()).startsWith("模型回复").endsWith("不构成投资建议。");
    assertThat(answer.sources())
        .singleElement()
        .satisfies(s -> assertThat(s.source()).isEqualTo("maotai.md"));
  }

  @Test
  void promptCarriesRetrievedContextAndUserQuestion() {
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(new Document("id1", "酱香型白酒 12987 工艺", Map.of("source", "maotai.md"))));

    service.chat("酿造工艺是什么？");

    ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
    verify(chatClient).prompt(captor.capture());
    assertThat(captor.getValue().getContents()).contains("12987 工艺").contains("酿造工艺是什么？");
  }

  @Test
  void refusesWithoutCallingModelWhenNoKnowledgeFound() {
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

    ChatAnswer answer = service.chat("特斯拉怎么样？");

    verify(chatClient, never()).prompt(any(Prompt.class)); // 无资料不幻觉：根本不调用模型
    assertThat(answer.sources()).isEmpty();
    assertThat(answer.reply()).contains("未找到").endsWith("不构成投资建议。");
  }
}
