package com.aievolution.rag;

import static org.assertj.core.api.Assertions.assertThat;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.qdrant.QdrantContainer;

/** 摄入管道集成测试：真实知识库文档 → 分块 → 写入真实 Qdrant 容器 → 检索召回正确来源。 */
@Testcontainers
class KnowledgeIngestionIT {

  @Container static final QdrantContainer QDRANT = new QdrantContainer("qdrant/qdrant:v1.15.1");

  @Test
  void ingestsKnowledgeBaseAndRetrievesRelevantChunk() throws Exception {
    try (QdrantClient client =
        new QdrantClient(
            QdrantGrpcClient.newBuilder(QDRANT.getHost(), QDRANT.getGrpcPort(), false).build())) {
      QdrantVectorStore store =
          QdrantVectorStore.builder(client, new TinyHashEmbeddingModel())
              .collectionName("knowledge_it")
              .initializeSchema(true)
              .build();
      // initializeSchema 的建表逻辑挂在 InitializingBean 回调上；非 Spring 托管时需手动触发
      store.afterPropertiesSet();

      new KnowledgeBaseIngestor(store, 800, "classpath:knowledge/*.md").run(null);

      List<Document> results =
          store.similaritySearch(SearchRequest.builder().query("酱香白酒的酿造工艺").topK(1).build());

      assertThat(results).isNotEmpty();
      assertThat(results.getFirst().getText()).contains("茅台");
      assertThat(results.getFirst().getMetadata()).containsEntry("source", "maotai.md");
    }
  }
}
