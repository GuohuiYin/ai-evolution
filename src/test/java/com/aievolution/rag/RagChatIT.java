package com.aievolution.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aievolution.chat.ChatAnswer;
import com.aievolution.chat.PromptLibrary;
import com.aievolution.chat.RagChatService;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.qdrant.QdrantContainer;

/**
 * RAG 全链路集成测试：真实 Qdrant 容器 + 真实知识库文档摄入 + mock 模型层， 验证"检索 → 上下文注入 Prompt → 引用溯源"完整闭环（不触网、不调真实模型）。
 */
@Testcontainers
class RagChatIT {

  @Container static final QdrantContainer QDRANT = new QdrantContainer("qdrant/qdrant:v1.15.1");

  @Test
  void ragFlowRetrievesAndCitesKnowledgeBase() throws Exception {
    try (QdrantClient client =
        new QdrantClient(
            QdrantGrpcClient.newBuilder(QDRANT.getHost(), QDRANT.getGrpcPort(), false).build())) {
      QdrantVectorStore store =
          QdrantVectorStore.builder(client, new TinyHashEmbeddingModel())
              .collectionName("rag_it")
              .initializeSchema(true)
              .build();
      store.afterPropertiesSet();
      new KnowledgeBaseIngestor(store).run(null);

      ChatClient.Builder builder = mock(ChatClient.Builder.class);
      ChatClient chatClient = mock(ChatClient.class);
      ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
      ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
      when(builder.build()).thenReturn(chatClient);
      when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
      when(requestSpec.call()).thenReturn(callSpec);
      when(callSpec.content()).thenReturn("基于资料的回答");

      // 测试替身的哈希向量得分分布与真实语义向量不同，集成测试放低阈值专注验证链路
      ChatAnswer answer =
          new RagChatService(builder, store, new PromptLibrary(), 0.0).chat("酱香白酒的酿造工艺");

      // 引用的来源必须命中知识库文档
      assertThat(answer.sources()).anySatisfy(s -> assertThat(s.source()).isEqualTo("maotai.md"));
      // 注入模型的 Prompt 必须真的带着知识库原文（12987 工艺仅存在于 maotai.md）
      ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
      verify(chatClient).prompt(captor.capture());
      assertThat(captor.getValue().getContents()).contains("12987");
      // 金融红线一：免责声明由服务端强制追加
      assertThat(answer.reply()).endsWith("不构成投资建议。");
    }
  }
}
