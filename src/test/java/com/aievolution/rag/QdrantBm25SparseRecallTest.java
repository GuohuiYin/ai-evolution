package com.aievolution.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.Futures;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Common.PointId;
import io.qdrant.client.grpc.JsonWithInt.Value;
import io.qdrant.client.grpc.Points.QueryPoints;
import io.qdrant.client.grpc.Points.ScoredPoint;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;

/**
 * W16 #2 A3-lite 查询侧契约：BM25 召回 = encodeQuery 出的稀疏向量在 "bm25" 命名向量上 nearest 查询（IDF 由服务端 modifier
 * 补齐），命中文档带真实 BM25 分； 故障降级空召回（增强路不击穿 dense 主路，与 matchtext 路同一韧性立场）。
 */
class QdrantBm25SparseRecallTest {

  private static final String POINT_ID = "6ba7b810-9dad-11d1-80b4-00c04fd430c8";

  private final QdrantClient client = mock(QdrantClient.class);
  private final QdrantBm25SparseRecall recall = new QdrantBm25SparseRecall(client, "kb", 1.5, 0.75);

  private static Value str(String s) {
    return Value.newBuilder().setStringValue(s).build();
  }

  @Test
  void recallQueriesNearestSparseOnBm25NamedVector() {
    ScoredPoint point =
        ScoredPoint.newBuilder()
            .setId(PointId.newBuilder().setUuid(POINT_ID).build())
            .setScore(2.5f)
            .putPayload("doc_content", str("品牌壁垒深厚的茅台"))
            .putPayload("docType", str("report"))
            .build();
    when(client.queryAsync(any(QueryPoints.class)))
        .thenReturn(Futures.immediateFuture(List.of(point)));

    List<Document> docs = recall.recall("茅台品牌壁垒", new KnowledgeFilter("report", "2024"), 10);

    ArgumentCaptor<QueryPoints> captor = ArgumentCaptor.forClass(QueryPoints.class);
    verify(client).queryAsync(captor.capture());
    QueryPoints q = captor.getValue();
    assertThat(q.getCollectionName()).isEqualTo("kb");
    assertThat(q.getUsing()).isEqualTo("bm25");
    assertThat(q.getLimit()).isEqualTo(10);
    // 查询侧稀疏向量：分词锚定词在场（「品牌」「壁垒」），服务端据此做 IDF 加权
    assertThat(q.getQuery().getNearest().getSparse().getIndicesList())
        .contains(Bm25Encoder.hash("品牌"), Bm25Encoder.hash("壁垒"));
    // 元数据过滤与 matchtext 路同语义：docType/asOf 进 must 条件
    assertThat(q.getFilter().getMustCount()).isEqualTo(2);

    assertThat(docs).hasSize(1);
    Document doc = docs.getFirst();
    assertThat(doc.getId()).isEqualTo(POINT_ID);
    assertThat(doc.getText()).isEqualTo("品牌壁垒深厚的茅台");
    assertThat(doc.getScore()).isEqualTo(2.5);
    assertThat(doc.getMetadata()).containsEntry("docType", "report");
  }

  @Test
  void blankQueryReturnsEmptyWithoutTouchingBackend() {
    assertThat(recall.recall("  ", KnowledgeFilter.NONE, 10)).isEmpty();
    verifyNoInteractions(client);
  }

  @Test
  void backendFailureDegradesToEmptyRecall() {
    when(client.queryAsync(any(QueryPoints.class)))
        .thenReturn(Futures.immediateFailedFuture(new RuntimeException("grpc boom")));

    assertThat(recall.recall("茅台品牌壁垒", KnowledgeFilter.NONE, 10)).isEmpty();
  }
}
