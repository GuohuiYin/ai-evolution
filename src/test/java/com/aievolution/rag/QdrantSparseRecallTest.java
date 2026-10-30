package com.aievolution.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.Futures;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.ValueFactory;
import io.qdrant.client.grpc.Common.PointId;
import io.qdrant.client.grpc.Points.RetrievedPoint;
import io.qdrant.client.grpc.Points.ScrollPoints;
import io.qdrant.client.grpc.Points.ScrollResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;

/** W13 #2b 降级语义单测：sparse 路是召回增强而非必经路，故障降级为空路不击穿 dense 主路。 */
class QdrantSparseRecallTest {

  @Test
  void grpcFailureDegradesToEmptyInsteadOfThrowing() {
    QdrantClient client = mock(QdrantClient.class);
    when(client.scrollAsync(any(ScrollPoints.class)))
        .thenThrow(new RuntimeException("grpc unavailable"));

    QdrantSparseRecall recall = new QdrantSparseRecall(client, "kb", 10_000);
    assertThat(recall.recall("12987", KnowledgeFilter.NONE, 5)).isEmpty();
  }

  @Test
  void blankQueryNeverTouchesGrpc() {
    QdrantClient client = mock(QdrantClient.class);
    QdrantSparseRecall recall = new QdrantSparseRecall(client, "kb", 10_000);

    assertThat(recall.recall("  ", KnowledgeFilter.NONE, 5)).isEmpty();
    verifyNoInteractions(client);
  }

  /** W25 #4：500 条单页截断修复——多页结果必须翻页取尽，且第二页请求携带游标。 */
  @Test
  void fetchPaginatesThroughAllPages() {
    QdrantClient client = mock(QdrantClient.class);
    String cursor = "00000000-0000-0000-0000-000000000002";
    when(client.scrollAsync(any(ScrollPoints.class)))
        .thenReturn(Futures.immediateFuture(page(List.of(point("动力电池装车量市占率第一")), cursor)))
        .thenReturn(Futures.immediateFuture(page(List.of(point("动力电池回收体系")), null)));

    QdrantSparseRecall recall = new QdrantSparseRecall(client, "kb", 10_000);
    List<Document> hits = recall.recall("动力电池", KnowledgeFilter.NONE, 10);

    assertThat(hits).hasSize(2);
    ArgumentCaptor<ScrollPoints> captor = ArgumentCaptor.forClass(ScrollPoints.class);
    verify(client, times(2)).scrollAsync(captor.capture());
    assertThat(captor.getAllValues().get(1).getOffset().getUuid()).isEqualTo(cursor);
  }

  /** W25 #4：触顶即截断且有 WARN——静默退化（W24→W25 教训）不许重演。 */
  @Test
  void safetyCapStopsPaging() {
    QdrantClient client = mock(QdrantClient.class);
    String cursor = "00000000-0000-0000-0000-000000000002";
    when(client.scrollAsync(any(ScrollPoints.class)))
        .thenReturn(Futures.immediateFuture(page(List.of(point("动力电池装车量市占率第一")), cursor)));

    QdrantSparseRecall recall = new QdrantSparseRecall(client, "kb", 1);
    List<Document> hits = recall.recall("动力电池", KnowledgeFilter.NONE, 10);

    assertThat(hits).hasSize(1);
    verify(client, times(1)).scrollAsync(any(ScrollPoints.class));
  }

  private static RetrievedPoint point(String content) {
    return RetrievedPoint.newBuilder()
        .setId(PointId.newBuilder().setUuid(UUID.randomUUID().toString()).build())
        .putPayload("doc_content", ValueFactory.value(content))
        .build();
  }

  private static ScrollResponse page(List<RetrievedPoint> points, String nextCursor) {
    ScrollResponse.Builder builder = ScrollResponse.newBuilder().addAllResult(points);
    if (nextCursor != null) {
      builder.setNextPageOffset(PointId.newBuilder().setUuid(nextCursor).build());
    }
    return builder.build();
  }
}
