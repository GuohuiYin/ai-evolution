package com.aievolution.rag;

import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 基于 Spring AI {@link VectorStore} 的 {@link KnowledgeRetriever} 实现。
 *
 * <p>检索调参（topK / similarity-threshold）从 {@code ai.rag.*} 配置注入，与 eval 验收门共用同一 配置键——"评估口径=线上口径"由配置保证。
 *
 * <p>W13 #2c 混合检索（ADR-0017）：{@code ai.rag.hybrid.enabled=true} 且 sparse 路 Bean 在场时， dense 路按 {@code
 * recall-top-k} 放大召回，与 sparse 字面路经 RRF 融合，最终截到 {@code top-k} （线上返回口径不变）。开关关闭或 sparse Bean
 * 缺席时行为与单阶段完全一致—— 默认关，黄金集裁决达标才启用（W13 #2 数据裁决）。
 */
@Component
public class VectorStoreKnowledgeRetriever implements KnowledgeRetriever {

  private static final Logger log = LoggerFactory.getLogger(VectorStoreKnowledgeRetriever.class);

  private final VectorStore vectorStore;
  private final double similarityThreshold;
  private final int topK;
  private final SparseRecall sparseRecall; // null = 无 sparse 路（单阶段召回）
  private final int recallTopK;
  private final RrfFuser rrfFuser;
  private final RerankerClient reranker; // null = 无精排段
  private final int rerankTopN;

  /** 单阶段构造（hybrid/rerank 全关语义）：测试与回退路径共用。 */
  public VectorStoreKnowledgeRetriever(
      VectorStore vectorStore, double similarityThreshold, int topK) {
    this(
        vectorStore,
        similarityThreshold,
        topK,
        Optional.empty(),
        false,
        20,
        60,
        Optional.empty(),
        false,
        5);
  }

  /** hybrid 接线构造（rerank 关语义）：#2 测试与回退路径共用。 */
  public VectorStoreKnowledgeRetriever(
      VectorStore vectorStore,
      double similarityThreshold,
      int topK,
      Optional<SparseRecall> sparseRecall,
      boolean hybridEnabled,
      int recallTopK,
      int rrfK) {
    this(
        vectorStore,
        similarityThreshold,
        topK,
        sparseRecall,
        hybridEnabled,
        recallTopK,
        rrfK,
        Optional.empty(),
        false,
        5);
  }

  @Autowired
  public VectorStoreKnowledgeRetriever(
      VectorStore vectorStore,
      @Value("${ai.rag.similarity-threshold:0.5}") double similarityThreshold,
      @Value("${ai.rag.top-k:5}") int topK,
      Optional<SparseRecall> sparseRecall,
      @Value("${ai.rag.hybrid.enabled:false}") boolean hybridEnabled,
      @Value("${ai.rag.recall-top-k:20}") int recallTopK,
      @Value("${ai.rag.hybrid.rrf-k:60}") int rrfK,
      Optional<RerankerClient> reranker,
      @Value("${ai.rag.rerank.enabled:false}") boolean rerankEnabled,
      @Value("${ai.rag.rerank.top-n:5}") int rerankTopN) {
    this.vectorStore = vectorStore;
    this.similarityThreshold = similarityThreshold;
    this.topK = topK;
    this.sparseRecall = hybridEnabled ? sparseRecall.orElse(null) : null;
    this.recallTopK = recallTopK;
    this.rrfFuser = new RrfFuser(rrfK);
    this.reranker = rerankEnabled ? reranker.orElse(null) : null;
    this.rerankTopN = rerankTopN;
  }

  @Override
  public List<Document> retrieve(String query) {
    return retrieve(query, KnowledgeFilter.NONE);
  }

  @Override
  public List<Document> retrieve(String query, KnowledgeFilter filter) {
    if (sparseRecall == null && reranker == null) {
      List<Document> hits = denseSearch(query, filter, topK);
      // 检索判罚依据单点留痕：hits=0 即拒答现场；topScore 即"差多少命中"的标尺
      log.info(
          "stage=RETRIEVE mode=single hits={} topScore={} threshold={} topK={} filter={} query={}",
          hits.size(),
          topScore(hits),
          similarityThreshold,
          topK,
          filter,
          abbreviate(query));
      return hits;
    }
    // 两阶段：召回段（可选混合检索）宽松过取 → 精排段（可选）重排取前 N
    List<Document> recalled = recall(query, filter);
    if (reranker == null) {
      List<Document> hits = recalled.stream().limit(topK).toList();
      log.info(
          "stage=RETRIEVE mode=hybrid recallHits={} returnTop={} recallTopK={} filter={} query={}",
          recalled.size(),
          hits.size(),
          recallTopK,
          filter,
          abbreviate(query));
      return hits;
    }
    if (recalled.isEmpty()) {
      // 空召回即拒答的现有语义不变（越界硬拒答是红线）；零候选不打 rerank 计费电话
      log.info(
          "stage=RETRIEVE mode=rerank recallHits=0 rerank=skipped filter={} query={}",
          filter,
          abbreviate(query));
      return List.of();
    }
    try {
      List<Document> reranked = reranker.rerank(query, recalled, rerankTopN);
      log.info(
          "stage=RETRIEVE mode=rerank recallHits={} rerankTop={} rerankTopScore={} filter={} query={}",
          recalled.size(),
          reranked.size(),
          topScore(reranked),
          filter,
          abbreviate(query));
      return reranked;
    } catch (RerankerClient.RerankException e) {
      // rerank 是增强段不是必经段（ADR-0017）：供应商故障降级回召回结果，不报错、不穿底
      List<Document> degraded = recalled.stream().limit(rerankTopN).toList();
      log.warn(
          "stage=RETRIEVE mode=rerank 精排故障降级回召回结果（{}），returnTop={} filter={} query={}",
          e.getMessage(),
          degraded.size(),
          filter,
          abbreviate(query));
      return degraded;
    }
  }

  /** 召回段：hybrid 在场则 dense+sparse 双路 RRF 融合，否则 dense 单路；统一按 recall-top-k 过取。 */
  private List<Document> recall(String query, KnowledgeFilter filter) {
    List<Document> dense = denseSearch(query, filter, recallTopK);
    if (sparseRecall == null) {
      return dense;
    }
    List<Document> sparse = sparseRecall.recall(query, filter, recallTopK);
    return rrfFuser.fuse(dense, sparse);
  }

  private List<Document> denseSearch(String query, KnowledgeFilter filter, int k) {
    return vectorStore.similaritySearch(
        SearchRequest.builder()
            .query(query)
            .topK(k)
            .similarityThreshold(similarityThreshold)
            .filterExpression(toFilterExpression(filter))
            .build());
  }

  private static String topScore(List<Document> hits) {
    return hits.stream()
        .map(Document::getScore)
        .filter(java.util.Objects::nonNull)
        .map(s -> String.format("%.2f", s))
        .findFirst()
        .orElse("--");
  }

  private static String abbreviate(String query) {
    String oneLine = query.replaceAll("\\s+", " ").trim();
    return oneLine.length() <= 30 ? oneLine : oneLine.substring(0, 30) + "…";
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
