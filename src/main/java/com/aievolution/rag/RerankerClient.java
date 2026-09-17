package com.aievolution.rag;

import java.util.List;
import org.springframework.ai.document.Document;

/**
 * 精排（rerank）能力的领域接口：给定查询与召回候选，输出按相关度重排的子集。
 *
 * <p>W13 #3（ADR-0017）：两阶段检索的第二段——召回侧宽松过取（recall-top-k）， reranker（cross-encoder 类）逐对打分精排取前
 * N。实现薄封装供应商端点 （当前：SiliconFlow bge-reranker-v2-m3，协议非 OpenAI 兼容，不进 Spring AI 模型抽象）。
 *
 * <p>故障语义：实现以 {@link RerankException} 抛出供应商故障，由调用方（检索器） 降级回召回结果——rerank 是增强段不是必经段（ADR-0017
 * 登记的韧性立场）。
 */
public interface RerankerClient {

  /**
   * 精排候选。
   *
   * @param query 原始查询
   * @param candidates 召回候选（顺序即召回排名）
   * @param topN 精排后保留条数
   * @return 按 reranker 分数降序的候选；{@link Document#getScore()} 承载 reranker 分数
   * @throws RerankException 供应商调用失败（超时/5xx/协议异常）
   */
  List<Document> rerank(String query, List<Document> candidates, int topN);

  /** 供应商故障专用异常：与系统 bug 区分，调用方只对它降级。 */
  class RerankException extends RuntimeException {
    public RerankException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
