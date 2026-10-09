package com.aievolution.rag;

import java.util.List;
import org.springframework.ai.document.Document;

/**
 * 知识检索能力的领域接口：给定问题，从知识库召回相关文档片段。
 *
 * <p>检索策略（topK、相似度阈值、未来的元数据过滤 / rerank）由实现统一收口；调用方只关心 "问题进、文档出"，不感知向量库与调参细节。
 */
public interface KnowledgeRetriever {

  /**
   * 语义检索知识库。
   *
   * @param query 检索问题，完整问句效果更好
   * @return 相关文档片段（按相似度降序）；空列表表示阈值内无命中
   */
  List<Document> retrieve(String query);

  /**
   * 带元数据过滤的语义检索（如"只查 2024 年报"）。
   *
   * @param filter 过滤条件；{@link KnowledgeFilter#NONE} 等价于 {@link #retrieve(String)}
   */
  List<Document> retrieve(String query, KnowledgeFilter filter);

  /**
   * 带证据的语义检索（W16 #1）：除命中清单外，返回 dense 段最高相似度（阈值判罚标尺）。 eval 链路用此采集逐案分数分布；线上问答通路仍用 {@link
   * #retrieve(String)}—— 两种口径共用同一份检索逻辑，证据只是同一判罚过程的读数外暴露。
   */
  RetrievalEvidence retrieveWithEvidence(String query);

  /** 带元数据过滤的证据检索；{@link KnowledgeFilter#NONE} 等价于 {@link #retrieveWithEvidence(String)} */
  RetrievalEvidence retrieveWithEvidence(String query, KnowledgeFilter filter);
}
