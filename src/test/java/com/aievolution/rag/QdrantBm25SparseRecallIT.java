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

/**
 * W16 #2 A3-lite 端到端集成测试：真实 Qdrant v1.18.2 容器上走通 "写入（应用侧 TF + 服务端 IDF modifier）→ 查询（nearest sparse
 * on bm25 命名向量）" 全链路。 直击 A1 probe 判决的中文病灶——「品牌壁垒深厚」整段单 token 致子串「品牌壁垒」零命中；应用侧分词后 BM25 必须把它排回 Top1。
 */
@Testcontainers
class QdrantBm25SparseRecallIT {

  @Container static final QdrantContainer QDRANT = new QdrantContainer("qdrant/qdrant:v1.18.2");

  private static final String COLLECTION = "w16_bm25_it";

  /** 摄入侧规整化由 KnowledgeBaseIngestor 完成，IT 直写需镜像同一规则。 */
  private static SparseVectorWriter.ChunkText chunk(
      String source, String text, Map<String, Object> metadata) {
    return new SparseVectorWriter.ChunkText(
        UUID.nameUUIDFromBytes((source + "#0").getBytes()).toString(),
        FullTextNormalizer.normalize(text));
  }

  @Test
  void substringQueryRanksFullTermDocTop1WithBm25Scores() throws Exception {
    try (QdrantClient client =
        new QdrantClient(
            QdrantGrpcClient.newBuilder(QDRANT.getHost(), QDRANT.getGrpcPort(), false).build())) {
      QdrantVectorStore store =
          QdrantVectorStore.builder(client, new TinyHashEmbeddingModel())
              .collectionName(COLLECTION)
              .initializeSchema(true)
              .build();
      store.afterPropertiesSet();

      List<SparseVectorWriter.ChunkText> chunks =
          List.of(
              chunk("maotai.md", "贵州茅台品牌壁垒深厚，提价能力强，毛利率常年维持高位", Map.of()),
              chunk("pingan.md", "平安银行业绩说明：零售转型深化，品牌认知度提升", Map.of()),
              chunk("catl.md", "动力电池技术壁垒高，产能持续扩张", Map.of()));
      // dense 侧同 point 写入（payload 供召回侧取回文本）
      store.add(
          chunks.stream()
              .map(c -> new Document(c.id(), c.text(), Map.of("docType", "note")))
              .toList());

      QdrantSparseVectorWriter writer = new QdrantSparseVectorWriter(client, COLLECTION, 1.5, 0.75);
      writer.ensureSchema();
      Bm25Encoder encoder = new Bm25Encoder(1.5, 0.75);
      double avgdl =
          chunks.stream().mapToInt(c -> encoder.termCount(c.text())).sum() / (double) chunks.size();
      writer.write(chunks, avgdl);

      QdrantBm25SparseRecall recall = new QdrantBm25SparseRecall(client, COLLECTION, 1.5, 0.75);

      // 病灶直击：A1 probe 里「品牌壁垒深厚」整段单 token，子串「品牌壁垒」零命中；
      // 应用侧分词 + 真 BM25 后，双词项命中的 maotai 必须 Top1 且分数显著领先单词项命中
      List<Document> hits = recall.recall("品牌壁垒", KnowledgeFilter.NONE, 5);
      assertThat(hits).isNotEmpty();
      assertThat(hits.getFirst().getText()).contains("品牌壁垒深厚");
      assertThat(hits.getFirst().getScore()).isPositive();
      if (hits.size() > 1) {
        assertThat(hits.getFirst().getScore()).isGreaterThan(hits.get(1).getScore());
      }
    }
  }

  @Test
  void filterNarrowsBm25Recall() throws Exception {
    try (QdrantClient client =
        new QdrantClient(
            QdrantGrpcClient.newBuilder(QDRANT.getHost(), QDRANT.getGrpcPort(), false).build())) {
      QdrantVectorStore store =
          QdrantVectorStore.builder(client, new TinyHashEmbeddingModel())
              .collectionName(COLLECTION + "_filter")
              .initializeSchema(true)
              .build();
      store.afterPropertiesSet();

      SparseVectorWriter.ChunkText note = chunk("note.md", "品牌壁垒是消费企业的核心护城河", Map.of());
      SparseVectorWriter.ChunkText report = chunk("report.md", "年报摘要：品牌壁垒支撑提价逻辑", Map.of());
      store.add(
          List.of(
              new Document(note.id(), note.text(), Map.of("docType", "note")),
              new Document(
                  report.id(), report.text(), Map.of("docType", "report", "asOf", "2024-12-31"))));

      QdrantSparseVectorWriter writer =
          new QdrantSparseVectorWriter(client, COLLECTION + "_filter", 1.5, 0.75);
      writer.ensureSchema();
      Bm25Encoder encoder = new Bm25Encoder(1.5, 0.75);
      double avgdl = (encoder.termCount(note.text()) + encoder.termCount(report.text())) / 2.0;
      writer.write(List.of(note, report), avgdl);

      QdrantBm25SparseRecall recall =
          new QdrantBm25SparseRecall(client, COLLECTION + "_filter", 1.5, 0.75);

      assertThat(recall.recall("品牌壁垒", KnowledgeFilter.NONE, 5)).hasSize(2);
      List<Document> reportsOnly =
          recall.recall("品牌壁垒", new KnowledgeFilter("report", "2024-12-31"), 5);
      assertThat(reportsOnly).hasSize(1);
      assertThat(reportsOnly.getFirst().getText()).contains("年报摘要");
    }
  }

  @Test
  void noSharedTermReturnsEmpty() throws Exception {
    try (QdrantClient client =
        new QdrantClient(
            QdrantGrpcClient.newBuilder(QDRANT.getHost(), QDRANT.getGrpcPort(), false).build())) {
      QdrantBm25SparseRecall recall = new QdrantBm25SparseRecall(client, COLLECTION, 1.5, 0.75);
      assertThat(recall.recall("绝不存在的术语甲乙丙", KnowledgeFilter.NONE, 5)).isEmpty();
      assertThat(recall.recall("   ", KnowledgeFilter.NONE, 5)).isEmpty();
    }
  }
}
