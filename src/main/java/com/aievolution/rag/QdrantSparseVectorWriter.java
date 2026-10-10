package com.aievolution.rag;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections.Modifier;
import io.qdrant.client.grpc.Common.PointId;
import io.qdrant.client.grpc.Points.CreateVectorNameRequest;
import io.qdrant.client.grpc.Points.NamedVectors;
import io.qdrant.client.grpc.Points.PointVectors;
import io.qdrant.client.grpc.Points.SparseVector;
import io.qdrant.client.grpc.Points.SparseVectorCreationConfig;
import io.qdrant.client.grpc.Points.Vector;
import io.qdrant.client.grpc.Points.Vectors;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * {@link SparseVectorWriter} 的 Qdrant 实现（W16 #2 A3-lite）：BM25 编码（{@link Bm25Encoder}， 应用侧只出
 * TF/长度归一）+ sparse 命名向量写入（IDF 由服务端 modifier 补，FastEmbed 同款分解）。
 *
 * <p>schema 自给：collection 缺 bm25 稀疏位时经 create_vector_name 幂等补齐（Qdrant ≥1.18， 免重建加向量——v1.18.2
 * 升版的前置动机）。与 dense 同 point 写入（确定性 ID）， 只更新 bm25 命名向量，dense 值不受影响。
 *
 * <p>只在 {@code ai.rag.sparse.impl=bm25} 时装配；与 matchtext 路互斥（对照实验切换面）。
 */
@Component
@ConditionalOnProperty(name = "ai.rag.sparse.impl", havingValue = "bm25")
public class QdrantSparseVectorWriter implements SparseVectorWriter, SmartInitializingSingleton {

  private static final Logger log = LoggerFactory.getLogger(QdrantSparseVectorWriter.class);

  /** sparse 命名向量名：写入/查询/schema 三处对齐的单点（跨类引用同名字面量锚定于此）。 */
  static final String SPARSE_VECTOR_NAME = "bm25";

  private final QdrantClient client;
  private final String collection;
  private final Bm25Encoder encoder;

  public QdrantSparseVectorWriter(
      QdrantClient client,
      @Value("${spring.ai.vectorstore.qdrant.collection-name:knowledge_base}") String collection,
      @Value("${ai.rag.sparse.bm25.k1:1.5}") double k1,
      @Value("${ai.rag.sparse.bm25.b:0.75}") double b) {
    this.client = client;
    this.collection = collection;
    this.encoder = new Bm25Encoder(k1, b);
  }

  @Override
  public void afterSingletonsInstantiated() {
    ensureSchema();
  }

  @Override
  public void ensureSchema() {
    try {
      boolean present =
          client
              .getCollectionInfoAsync(collection)
              .get()
              .getConfig()
              .getParams()
              .getSparseVectorsConfig()
              .getMap()
              .containsKey(SPARSE_VECTOR_NAME);
      if (present) {
        return;
      }
      client
          .createVectorNameAsync(
              CreateVectorNameRequest.newBuilder()
                  .setCollectionName(collection)
                  .setVectorName(SPARSE_VECTOR_NAME)
                  .setSparseConfig(
                      SparseVectorCreationConfig.newBuilder().setModifier(Modifier.Idf).build())
                  .build())
          .get();
      log.info(
          "BM25 稀疏命名向量已补齐：collection={} vector={} modifier=IDF", collection, SPARSE_VECTOR_NAME);
    } catch (Exception e) {
      // collection 尚未创建（initialize-schema 关）等场景：告警不阻断，摄入阶段会显式失败
      log.warn("BM25 稀疏向量 schema 确认未成功（collection 未建？）：{}", e.getMessage());
    }
  }

  @Override
  public int tokenCount(String text) {
    return encoder.termCount(text);
  }

  @Override
  public void write(List<ChunkText> chunks, double avgDocLength) {
    if (chunks.isEmpty()) {
      return;
    }
    List<PointVectors> points = chunks.stream().map(c -> toPointVectors(c, avgDocLength)).toList();
    try {
      client.updateVectorsAsync(collection, points).get();
    } catch (Exception e) {
      // 稀疏向量是增强路附件：写入失败显式报错（摄入一致性 > 静默降级——dense/sparse 不同步
      // 会产出"有 dense 无 sparse"的半残 point，比对失败更难查）
      throw new IllegalStateException("BM25 稀疏向量写入失败: collection=" + collection, e);
    }
  }

  private PointVectors toPointVectors(ChunkText chunk, double avgDocLength) {
    Bm25Encoder.SparseVector v = encoder.encodeDocument(chunk.text(), avgDocLength);
    SparseVector sparse =
        SparseVector.newBuilder()
            .addAllIndices(QdrantPayloads.toIntList(v.indices()))
            .addAllValues(QdrantPayloads.toFloatList(v.values()))
            .build();
    return PointVectors.newBuilder()
        .setId(PointId.newBuilder().setUuid(chunk.id()).build())
        .setVectors(
            Vectors.newBuilder()
                .setVectors(
                    NamedVectors.newBuilder()
                        .putVectors(
                            SPARSE_VECTOR_NAME, Vector.newBuilder().setSparse(sparse).build())))
        .build();
  }
}
