package com.aievolution.rag;

import java.util.List;
import org.springframework.ai.document.Document;

/**
 * 字面（关键词）召回路：混合检索的第二路，与 dense 向量路互补—— 向量路对数字代号/专有名词/原文片段的字面差异不敏感（W8 基线 + W13 #1 三轮对照实证）， 字面路正对靶心。
 *
 * <p>接口收口（A11-3）：实现二选一经 {@code ai.rag.sparse.impl} 切换——matchtext（Qdrant payload 全文索引，BM25 轻量近似）/
 * bm25（W16 #2 A3-lite 真 BM25 稀疏向量，ADR-0017 候选 A 落地形态）， 调用方不感知。
 *
 * <p>实现互斥装配，是否启用由消费侧 {@code hybrid.enabled} 门控。
 */
public interface SparseRecall {

  /**
   * 按字面匹配召回。
   *
   * @param query 检索问题（分词由实现侧负责）
   * @param filter 元数据过滤；{@link KnowledgeFilter#NONE} 不过滤
   * @param limit 召回上限（即 recall-top-k）
   * @return 命中文档。matchtext 路无相似度分数（命中/不命中，融合按返回顺序取排名）； bm25 路带真实 BM25 分（RRF 仍按排名融合，分数供观测）
   */
  List<Document> recall(String query, KnowledgeFilter filter, int limit);
}
