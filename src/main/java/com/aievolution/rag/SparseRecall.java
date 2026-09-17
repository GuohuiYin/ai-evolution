package com.aievolution.rag;

import java.util.List;
import org.springframework.ai.document.Document;

/**
 * 字面（关键词）召回路：混合检索的第二路，与 dense 向量路互补—— 向量路对数字代号/专有名词/原文片段的字面差异不敏感（W8 基线 + W13 #1 三轮对照实证）， 字面路正对靶心。
 *
 * <p>接口收口（A11-3）：当前实现是 Qdrant payload 全文索引（MatchText），属 BM25 的轻量近似； 数据不达标时的升级路径是原生 BM25
 * 稀疏向量（ADR-0017 候选 A），调用方不感知。
 */
public interface SparseRecall {

  /**
   * 按字面匹配召回。
   *
   * @param query 检索问题（分词由底层索引负责）
   * @param filter 元数据过滤；{@link KnowledgeFilter#NONE} 不过滤
   * @param limit 召回上限（即 recall-top-k）
   * @return 命中文档（无相似度分数——字面路只有命中/不命中，排序信号弱，融合按返回顺序取排名）
   */
  List<Document> recall(String query, KnowledgeFilter filter, int limit);
}
