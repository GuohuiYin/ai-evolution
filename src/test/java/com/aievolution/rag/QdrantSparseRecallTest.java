package com.aievolution.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points.ScrollPoints;
import org.junit.jupiter.api.Test;

/** W13 #2b 降级语义单测：sparse 路是召回增强而非必经路，故障降级为空路不击穿 dense 主路。 */
class QdrantSparseRecallTest {

  @Test
  void grpcFailureDegradesToEmptyInsteadOfThrowing() {
    QdrantClient client = mock(QdrantClient.class);
    when(client.scrollAsync(any(ScrollPoints.class)))
        .thenThrow(new RuntimeException("grpc unavailable"));

    QdrantSparseRecall recall = new QdrantSparseRecall(client, "kb");
    assertThat(recall.recall("12987", KnowledgeFilter.NONE, 5)).isEmpty();
  }

  @Test
  void blankQueryNeverTouchesGrpc() {
    QdrantClient client = mock(QdrantClient.class);
    QdrantSparseRecall recall = new QdrantSparseRecall(client, "kb");

    assertThat(recall.recall("  ", KnowledgeFilter.NONE, 5)).isEmpty();
    verifyNoInteractions(client);
  }
}
