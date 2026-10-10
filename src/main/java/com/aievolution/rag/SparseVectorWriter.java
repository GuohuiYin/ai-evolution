package com.aievolution.rag;

import java.util.List;

/**
 * BM25 稀疏向量写入能力（W16 #2 A3-lite）：摄入链路把 chunk 文本交给本接口， 由实现完成 BM25 编码并写入向量库的 sparse 命名向量（与 dense 同
 * point）。
 *
 * <p>BM25 设施细节（编码、schema、传输）全部收口在实现侧（A11-3）——摄入器只感知 "词项统计 + 写入"两件领域事实。env 切换装配（{@code
 * ai.rag.sparse.impl=bm25}）， 与 matchtext 路构成对照实验的唯一变量面。
 */
public interface SparseVectorWriter {

  /** 待写入 chunk：确定性 point ID（与 dense 侧同一 ID 语义）+ 规整化文本。 */
  record ChunkText(String id, String text) {}

  /** 幂等确保向量库携带 BM25 稀疏命名向量（IDF modifier）；Bean 装配即就绪。 */
  void ensureSchema();

  /** 词项计数（avgdl 语料均值与 manifest tokenSum 的统计原料）：与写入侧同一分词/编码口径。 */
  int tokenCount(String text);

  /**
   * 编码并写入一批 chunk 的稀疏向量。
   *
   * @param avgDocLength 全语料平均词项数（BM25 长度归一的分母锚点）
   */
  void write(List<ChunkText> chunks, double avgDocLength);
}
