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
}
