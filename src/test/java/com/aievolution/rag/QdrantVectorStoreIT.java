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

/** W3-Step1 集成测试基座：在真实 Qdrant 容器里验证"写入向量 → 相似度检索"全链路。 Embedding 用确定性测试替身，本测试不触网、不依赖任何 API Key。 */
@Testcontainers
class QdrantVectorStoreIT {

  @Container static final QdrantContainer QDRANT = new QdrantContainer("qdrant/qdrant:v1.15.1");

  @Test
  void similaritySearchReturnsSemanticallyClosestDocument() throws Exception {
    try (QdrantClient client =
        new QdrantClient(
            QdrantGrpcClient.newBuilder(QDRANT.getHost(), QDRANT.getGrpcPort(), false).build())) {
      QdrantVectorStore store =
          QdrantVectorStore.builder(client, new TinyHashEmbeddingModel())
              .collectionName("w3_step1_it")
              .initializeSchema(true)
              .build();
      // initializeSchema 的建表逻辑挂在 InitializingBean 回调上；非 Spring 托管时需手动触发
      store.afterPropertiesSet();

      store.add(
          List.of(
              new Document("贵州茅台是中国白酒龙头，主营酱香型白酒的酿造与销售"),
              new Document("宁德时代是全球领先的动力电池制造商，主营锂电池研发与生产"),
              new Document("招商银行是股份制商业银行，主营零售金融业务")));

      List<Document> results =
          store.similaritySearch(SearchRequest.builder().query("白酒的酿造工艺").topK(1).build());

      assertThat(results).hasSize(1);
      assertThat(results.getFirst().getText()).contains("茅台");
    }
  }
}
