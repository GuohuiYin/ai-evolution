package com.aievolution.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.Futures;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections.CollectionInfo;
import io.qdrant.client.grpc.Collections.Modifier;
import io.qdrant.client.grpc.Collections.SparseVectorParams;
import io.qdrant.client.grpc.Points.CreateVectorNameRequest;
import io.qdrant.client.grpc.Points.PointVectors;
import io.qdrant.client.grpc.Points.UpdateResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Qdrant 稀疏向量写入器单测（W16 #2 A3-lite）：schema 幂等补齐（缺 bm25 位才 create_vector_name，IDF modifier 是 BM25
 * 的服务端半边）+ 稀疏点向量组装 （indices/values 一一对齐，uuid 入 point id）。
 */
class QdrantSparseVectorWriterTest {

  private final QdrantClient client = mock(QdrantClient.class);
  private final QdrantSparseVectorWriter writer =
      new QdrantSparseVectorWriter(client, "kb", 1.5, 0.75);

  @Test
  void ensureSchemaAddsSparseVectorWithIdfModifierWhenMissing() {
    when(client.getCollectionInfoAsync("kb"))
        .thenReturn(Futures.immediateFuture(CollectionInfo.getDefaultInstance()));
    when(client.createVectorNameAsync(any(CreateVectorNameRequest.class)))
        .thenReturn(Futures.immediateFuture(UpdateResult.getDefaultInstance()));

    writer.ensureSchema();

    ArgumentCaptor<CreateVectorNameRequest> captor =
        ArgumentCaptor.forClass(CreateVectorNameRequest.class);
    verify(client).createVectorNameAsync(captor.capture());
    CreateVectorNameRequest req = captor.getValue();
    assertThat(req.getCollectionName()).isEqualTo("kb");
    assertThat(req.getVectorName()).isEqualTo("bm25");
    // IDF modifier 是整条 A3-lite 的服务端锚点：应用侧只出 TF，IDF 由 Qdrant 补
    assertThat(req.getSparseConfig().getModifier()).isEqualTo(Modifier.Idf);
  }

  @Test
  void ensureSchemaIsNoopWhenSparseVectorAlreadyPresent() {
    CollectionInfo present =
        CollectionInfo.newBuilder()
            .setConfig(
                io.qdrant.client.grpc.Collections.CollectionConfig.newBuilder()
                    .setParams(
                        io.qdrant.client.grpc.Collections.CollectionParams.newBuilder()
                            .setSparseVectorsConfig(
                                io.qdrant.client.grpc.Collections.SparseVectorConfig.newBuilder()
                                    .putMap("bm25", SparseVectorParams.getDefaultInstance()))))
            .build();
    when(client.getCollectionInfoAsync("kb")).thenReturn(Futures.immediateFuture(present));

    writer.ensureSchema();

    verify(client, never()).createVectorNameAsync(any());
  }

  @Test
  void writeAssemblesSparsePointVectorsWithAlignedIndicesAndValues() {
    when(client.updateVectorsAsync(eq("kb"), any()))
        .thenReturn(Futures.immediateFuture(UpdateResult.getDefaultInstance()));

    writer.write(
        List.of(
            new SparseVectorWriter.ChunkText(
                "6ba7b810-9dad-11d1-80b4-00c04fd430c8", "品牌壁垒深厚，提价能力强")),
        3.0);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<PointVectors>> captor = ArgumentCaptor.forClass(List.class);
    verify(client).updateVectorsAsync(eq("kb"), captor.capture());
    List<PointVectors> points = captor.getValue();
    assertThat(points).hasSize(1);
    PointVectors point = points.getFirst();
    assertThat(point.getId().getUuid()).isEqualTo("6ba7b810-9dad-11d1-80b4-00c04fd430c8");
    var sparse = point.getVectors().getVectors().getVectorsOrThrow("bm25").getSparse();
    // indices/values 一一对齐是稀疏向量的结构底线；「品牌」「壁垒」两个锚定词必须在场
    assertThat(sparse.getIndicesCount()).isEqualTo(sparse.getValuesCount());
    assertThat(sparse.getIndicesList()).contains(Bm25Encoder.hash("品牌"), Bm25Encoder.hash("壁垒"));
  }
}
