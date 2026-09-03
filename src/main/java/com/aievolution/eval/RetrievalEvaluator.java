package com.aievolution.eval;

import java.util.List;
import java.util.Objects;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.lang.Nullable;

/**
 * 检索评估器（eval 雏形的核心）：用黄金集度量"该召回的召回了、不该召回的没召回"。 与具体 Embedding 模型解耦——CI 里用确定性测试替身跑正例，本地用真实 bge-m3
 * 跑全量（含负例）。
 */
public class RetrievalEvaluator {

  private final VectorStore vectorStore;
  private final double similarityThreshold;

  public RetrievalEvaluator(VectorStore vectorStore, double similarityThreshold) {
    this.vectorStore = vectorStore;
    this.similarityThreshold = similarityThreshold;
  }

  public List<EvalResult> evaluate(List<GoldenCase> cases) {
    return cases.stream().map(this::evaluateOne).toList();
  }

  private EvalResult evaluateOne(GoldenCase goldenCase) {
    List<Document> results =
        vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(goldenCase.query())
                .topK(1)
                .similarityThreshold(similarityThreshold)
                .build());
    String actualSource =
        results.isEmpty() ? null : String.valueOf(results.getFirst().getMetadata().get("source"));
    return new EvalResult(
        goldenCase, actualSource, Objects.equals(goldenCase.expectSource(), actualSource));
  }

  /**
   * @param actualSource 实际 Top-1 命中来源；{@code null} 表示未命中任何文档
   */
  public record EvalResult(GoldenCase goldenCase, @Nullable String actualSource, boolean pass) {}
}
