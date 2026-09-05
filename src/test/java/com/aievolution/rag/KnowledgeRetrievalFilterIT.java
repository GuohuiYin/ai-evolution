package com.aievolution.rag;

import static org.assertj.core.api.Assertions.assertThat;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.qdrant.QdrantContainer;

/** 元数据过滤能力的真实向量库验证（W5-Step4 退出条件②：如"只查 2024 年报"）。 */
@Testcontainers
class KnowledgeRetrievalFilterIT {

  @Container static final QdrantContainer QDRANT = new QdrantContainer("qdrant/qdrant:v1.15.1");

  @Test
  void filterRestrictsResultsToMatchingMetadata() throws Exception {
    try (QdrantClient client =
        new QdrantClient(
            QdrantGrpcClient.newBuilder(QDRANT.getHost(), QDRANT.getGrpcPort(), false).build())) {
      QdrantVectorStore store =
          QdrantVectorStore.builder(client, new TinyHashEmbeddingModel())
              .collectionName("filter_it")
              .initializeSchema(true)
              .build();
      store.afterPropertiesSet();
      store.add(
          List.of(
              new Document(
                  UUID.nameUUIDFromBytes("report".getBytes()).toString(),
                  "年报 营收 数据",
                  Map.of("source", "annual-report.pdf", "docType", "report", "asOf", "2024-12-31")),
              new Document(
                  UUID.nameUUIDFromBytes("note".getBytes()).toString(),
                  "年报 笔记 随笔",
                  Map.of("source", "note.md", "docType", "note"))));

      KnowledgeRetriever retriever = new VectorStoreKnowledgeRetriever(store, 0.0, 5);
      List<Document> results =
          retriever.retrieve("年报", new KnowledgeFilter("report", "2024-12-31"));

      assertThat(results)
          .isNotEmpty()
          .allSatisfy(
              d -> {
                assertThat(d.getMetadata()).containsEntry("docType", "report");
                assertThat(d.getMetadata()).containsEntry("asOf", "2024-12-31");
              });
    }
  }
}
