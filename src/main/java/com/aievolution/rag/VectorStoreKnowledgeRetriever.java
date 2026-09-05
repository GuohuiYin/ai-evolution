package com.aievolution.rag;

import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 基于 Spring AI {@link VectorStore} 的 {@link KnowledgeRetriever} 实现。
 *
 * <p>检索调参（topK / similarity-threshold）从 {@code ai.rag.*} 配置注入，与 eval 验收门共用同一 配置键——"评估口径=线上口径"由配置保证。
 */
@Component
public class VectorStoreKnowledgeRetriever implements KnowledgeRetriever {

  private final VectorStore vectorStore;
  private final double similarityThreshold;
  private final int topK;

  public VectorStoreKnowledgeRetriever(
      VectorStore vectorStore,
      @Value("${ai.rag.similarity-threshold:0.5}") double similarityThreshold,
      @Value("${ai.rag.top-k:5}") int topK) {
    this.vectorStore = vectorStore;
    this.similarityThreshold = similarityThreshold;
    this.topK = topK;
  }

  @Override
  public List<Document> retrieve(String query) {
    return retrieve(query, KnowledgeFilter.NONE);
  }

  @Override
  public List<Document> retrieve(String query, KnowledgeFilter filter) {
    return vectorStore.similaritySearch(
        SearchRequest.builder()
            .query(query)
            .topK(topK)
            .similarityThreshold(similarityThreshold)
            .filterExpression(toFilterExpression(filter))
            .build());
  }

  /** 领域过滤条件 → Spring AI 过滤表达式；null 字段不参与过滤。 */
  private Filter.Expression toFilterExpression(KnowledgeFilter filter) {
    FilterExpressionBuilder builder = new FilterExpressionBuilder();
    FilterExpressionBuilder.Op op = null;
    if (filter.docType() != null) {
      op = builder.eq("docType", filter.docType());
    }
    if (filter.asOf() != null) {
      FilterExpressionBuilder.Op asOfOp = builder.eq("asOf", filter.asOf());
      op = op == null ? asOfOp : builder.and(op, asOfOp);
    }
    return op == null ? null : op.build();
  }
}
