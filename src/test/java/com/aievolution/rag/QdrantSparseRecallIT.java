package com.aievolution.rag;

import static org.assertj.core.api.Assertions.assertThat;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.qdrant.QdrantContainer;

/**
 * W13 #2b 集成测试：sparse 字面召回路在真实 Qdrant 容器里验证——payload 全文索引（multilingual 分词，覆盖中文）+ MatchText 过滤。正对
 * W8/W13 #1 实证的字面匹配病灶（12987 数字代号、原文片段）。
 */
@Testcontainers
class QdrantSparseRecallIT {

  @Container static final QdrantContainer QDRANT = new QdrantContainer("qdrant/qdrant:v1.15.1");

  private static final String COLLECTION = "w13_sparse_it";

  /** Qdrant 全文索引对存量点异步构建（实测：建索引后立刻查返回 0），轮询等就绪。 */
  private static List<Document> recallWhenReady(
      QdrantSparseRecall recall, String query, KnowledgeFilter filter, int limit)
      throws InterruptedException {
    for (int i = 0; i < 20; i++) {
      List<Document> hits = recall.recall(query, filter, limit);
      if (!hits.isEmpty()) {
        return hits;
      }
      Thread.sleep(250);
    }
    return List.of();
  }

  @Test
  void literalKeywordRecallFindsDocDenseMisses() throws Exception {
    try (QdrantClient client =
        new QdrantClient(
            QdrantGrpcClient.newBuilder(QDRANT.getHost(), QDRANT.getGrpcPort(), false).build())) {
      QdrantVectorStore store =
          QdrantVectorStore.builder(client, new TinyHashEmbeddingModel())
              .collectionName(COLLECTION)
              .initializeSchema(true)
              .build();
      store.afterPropertiesSet();
      // 摄入侧规整化由 KnowledgeBaseIngestor 完成，IT 直写向量库需镜像同一规则
      store.add(
          List.of(
              new Document(
                  FullTextNormalizer.normalize("酿造工艺遵循12987流程：一年生产周期、两次投料、九次蒸煮"),
                  Map.of("source", "maotai.md", "docType", "note")),
              new Document(
                  "神行超充电池与麒麟电池是宁德时代的主力产品", Map.of("source", "catl.md", "docType", "note"))));

      QdrantSparseRecall recall = new QdrantSparseRecall(client, COLLECTION);
      recall.ensureIndex();

      // 12987 数字代号：dense 路三轮对照全部未召回（w13-chunk-size-comparison），字面路必须命中
      // 用黄金集真实问句（规整化后 12987 成独立 token）；孤立数字单 token 属已登记残余限制
      List<Document> hits = recallWhenReady(recall, "12987 工艺的具体含义", KnowledgeFilter.NONE, 5);
      assertThat(hits).hasSize(1);
      assertThat(hits.getFirst().getMetadata()).containsEntry("source", "maotai.md");
      assertThat(hits.getFirst().getText()).contains("12987");
    }
  }

  @Test
  void filterNarrowsSparseRecall() throws Exception {
    try (QdrantClient client =
        new QdrantClient(
            QdrantGrpcClient.newBuilder(QDRANT.getHost(), QDRANT.getGrpcPort(), false).build())) {
      QdrantVectorStore store =
          QdrantVectorStore.builder(client, new TinyHashEmbeddingModel())
              .collectionName(COLLECTION + "_filter")
              .initializeSchema(true)
              .build();
      store.afterPropertiesSet();
      store.add(
          List.of(
              new Document("动力电池装车量市占率第一", Map.of("source", "catl.md", "docType", "note")),
              new Document(
                  "动力电池年报摘要与风险提示",
                  Map.of("source", "catl-ar.pdf", "docType", "report", "asOf", "2024-12-31"))));

      QdrantSparseRecall recall = new QdrantSparseRecall(client, COLLECTION + "_filter");
      recall.ensureIndex();

      List<Document> all = recallWhenReady(recall, "动力电池", KnowledgeFilter.NONE, 5);
      assertThat(all).hasSize(2);
      List<Document> reportsOnly = recall.recall("动力电池", new KnowledgeFilter("report", null), 5);
      assertThat(reportsOnly).hasSize(1);
      assertThat(reportsOnly.getFirst().getMetadata()).containsEntry("source", "catl-ar.pdf");
    }
  }

  @Test
  void identifierFallsBackToFullScanWhenNetMisses() throws Exception {
    try (QdrantClient client =
        new QdrantClient(
            QdrantGrpcClient.newBuilder(QDRANT.getHost(), QDRANT.getGrpcPort(), false).build())) {
      QdrantVectorStore store =
          QdrantVectorStore.builder(client, new TinyHashEmbeddingModel())
              .collectionName(COLLECTION + "_fallback")
              .initializeSchema(true)
              .build();
      store.afterPropertiesSet();
      store.add(
          List.of(
              new Document(
                  FullTextNormalizer.normalize("酿造工艺遵循12987流程：一年生产周期"),
                  Map.of("source", "maotai.md", "docType", "note")),
              new Document("动力电池装车量市占率第一", Map.of("source", "catl.md", "docType", "note"))));

      QdrantSparseRecall recall = new QdrantSparseRecall(client, COLLECTION + "_fallback");
      recall.ensureIndex();

      // 中文部分（"到底什么梗"）在任何文档都不存在：候选网必空收，
      // 标识符 12987 触发兜底全扫，覆盖率层命中 maotai.md
      List<Document> hits = recallWhenReady(recall, "12987 到底什么梗", KnowledgeFilter.NONE, 5);
      assertThat(hits).hasSize(1);
      assertThat(hits.getFirst().getMetadata()).containsEntry("source", "maotai.md");
    }
  }

  @Test
  void noMatchAndBlankQueryReturnEmpty() throws Exception {
    try (QdrantClient client =
        new QdrantClient(
            QdrantGrpcClient.newBuilder(QDRANT.getHost(), QDRANT.getGrpcPort(), false).build())) {
      QdrantSparseRecall recall = new QdrantSparseRecall(client, COLLECTION);
      assertThat(recall.recall("绝不存在的术语甲乙丙", KnowledgeFilter.NONE, 5)).isEmpty();
      assertThat(recall.recall("   ", KnowledgeFilter.NONE, 5)).isEmpty();
    }
  }
}
