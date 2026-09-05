package com.aievolution.rag;

import static org.assertj.core.api.Assertions.assertThat;

import com.aievolution.eval.GoldenCase;
import com.aievolution.eval.RetrievalEvalRunner;
import com.aievolution.eval.RetrievalEvaluator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.qdrant.QdrantContainer;

/**
 * 黄金集检索回归（CI 版）：用确定性测试替身在真实 Qdrant 容器里跑黄金集的正例。 负例（"不该召回"）的判定依赖真实语义向量的距离分布，由本地 eval Runner（真实
 * bge-m3）覆盖。
 */
@Testcontainers
class GoldenRetrievalEvalIT {

  @Container static final QdrantContainer QDRANT = new QdrantContainer("qdrant/qdrant:v1.15.1");

  @Test
  void normalPositiveGoldenCasesAllHitExpectedSource() throws Exception {
    try (QdrantClient client =
        new QdrantClient(
            QdrantGrpcClient.newBuilder(QDRANT.getHost(), QDRANT.getGrpcPort(), false).build())) {
      QdrantVectorStore store =
          QdrantVectorStore.builder(client, new TinyHashEmbeddingModel())
              .collectionName("golden_eval_it")
              .initializeSchema(true)
              .build();
      store.afterPropertiesSet();
      new KnowledgeBaseIngestor(store).run(null);

      // 只回归 normal 正例：boundary/adversarial 的判定依赖真实语义向量的距离分布，
      // 由本地 eval Runner（真实 bge-m3）覆盖
      List<GoldenCase> normalPositiveCases =
          new ObjectMapper()
                  .readerForListOf(GoldenCase.class)
                  .<List<GoldenCase>>readValue(
                      new ClassPathResource("eval/golden-set.json").getInputStream())
                  .stream()
                  .filter(c -> c.expectSource() != null && "normal".equals(c.category()))
                  .toList();

      // 测试替身的哈希向量得分分布与真实模型不同，阈值放 0 专注验证"Recall@5 命中正确来源"
      List<RetrievalEvaluator.EvalResult> results =
          new RetrievalEvaluator(store, 0.0, RetrievalEvalRunner.TOP_K)
              .evaluate(normalPositiveCases);

      assertThat(results)
          .isNotEmpty()
          .allSatisfy(
              r ->
                  assertThat(r.pass())
                      .as("用例 [%s] 应命中 %s", r.goldenCase().query(), r.goldenCase().expectSource())
                      .isTrue());
    }
  }
}
