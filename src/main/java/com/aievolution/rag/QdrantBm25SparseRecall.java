package com.aievolution.rag;

import io.qdrant.client.ConditionFactory;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QueryFactory;
import io.qdrant.client.WithPayloadSelectorFactory;
import io.qdrant.client.grpc.Common.Filter;
import io.qdrant.client.grpc.Points.QueryPoints;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.SparseVector;
import io.qdrant.client.grpc.Points.VectorInput;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * {@link SparseRecall} 的 BM25 实现（W16 #2 A3-lite，ADR-0017 候选 A 的应用侧形态）： 查询经 {@link
 * Bm25Encoder#encodeQuery} 编码为稀疏向量，在 "bm25" 命名向量上 nearest 查询—— TF 饱和/长度归一在应用侧（与摄入写入同一编码器，口径单点），IDF
 * 由 Qdrant 服务端 modifier 补。
 *
 * <p>与 matchtext 路的差异：真实 BM25 分替代"命中/不命中"弱排序信号；候选网/覆盖率裁决/ 标识符兜底等应用侧补偿全部退役——分词-索引-打分口径一致后不再需要（A1
 * probe 判决： 病灶在服务端分词，不在 BM25 本身）。
 *
 * <p>韧性语义同 matchtext 路：sparse 是增强路——gRPC 故障降级为空召回，dense 主路照常。
 *
 * <p>装配互斥：只在 {@code ai.rag.sparse.impl=bm25} 时在场；是否启用由消费侧 {@code hybrid.enabled} 门控（{@link
 * VectorStoreKnowledgeRetriever}），与 matchtext 路同。
 */
@Component
@ConditionalOnProperty(name = "ai.rag.sparse.impl", havingValue = "bm25")
public class QdrantBm25SparseRecall implements SparseRecall {

  private static final Logger log = LoggerFactory.getLogger(QdrantBm25SparseRecall.class);

  // 与 QdrantVectorStore.DEFAULT_CONTENT_FIELD_NAME 同值（包私有不可引用，字面量对齐并注释锚定）
  private static final String CONTENT_FIELD = "doc_content";

  private final QdrantClient client;
  private final String collection;
  private final Bm25Encoder encoder;

  public QdrantBm25SparseRecall(
      QdrantClient client,
      @Value("${spring.ai.vectorstore.qdrant.collection-name:knowledge_base}") String collection,
      @Value("${ai.rag.sparse.bm25.k1:1.5}") double k1,
      @Value("${ai.rag.sparse.bm25.b:0.75}") double b) {
    this.client = client;
    this.collection = collection;
    this.encoder = new Bm25Encoder(k1, b);
  }

  @Override
  public List<Document> recall(String query, KnowledgeFilter filter, int limit) {
    if (query == null || query.isBlank()) {
      return List.of();
    }
    Bm25Encoder.SparseVector encoded = encoder.encodeQuery(FullTextNormalizer.normalize(query));
    if (encoded.indices().length == 0) {
      return List.of();
    }
    Filter.Builder qdrantFilter = Filter.newBuilder();
    if (filter.docType() != null) {
      qdrantFilter.addMust(ConditionFactory.matchKeyword("docType", filter.docType()));
    }
    if (filter.asOf() != null) {
      qdrantFilter.addMust(ConditionFactory.matchKeyword("asOf", filter.asOf()));
    }
    QueryPoints queryPoints =
        QueryPoints.newBuilder()
            .setCollectionName(collection)
            .setQuery(
                QueryFactory.nearest(
                    VectorInput.newBuilder()
                        .setSparse(
                            SparseVector.newBuilder()
                                .addAllIndices(QdrantPayloads.toIntList(encoded.indices()))
                                .addAllValues(QdrantPayloads.toFloatList(encoded.values())))
                        .build()))
            .setUsing(QdrantSparseVectorWriter.SPARSE_VECTOR_NAME)
            .setFilter(qdrantFilter)
            .setLimit(limit)
            .setWithPayload(WithPayloadSelectorFactory.enable(true))
            .build();
    try {
      return client.queryAsync(queryPoints).get().stream().map(this::toDocument).toList();
    } catch (Exception e) {
      // 增强路故障不击穿主路：降级为空召回，dense 结果照常服务
      log.warn("BM25 sparse 召回失败，降级为空路（dense 照常）：{}", e.getMessage());
      return List.of();
    }
  }

  private Document toDocument(ScoredPoint point) {
    Map<String, Object> metadata = QdrantPayloads.toPlainMap(point.getPayloadMap());
    String content = (String) metadata.remove(CONTENT_FIELD);
    return Document.builder()
        .id(point.getId().getUuid())
        .text(content)
        .metadata(metadata)
        .score((double) point.getScore())
        .build();
  }
}
