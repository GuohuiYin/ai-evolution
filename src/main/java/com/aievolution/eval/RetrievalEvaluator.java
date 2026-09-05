package com.aievolution.eval;

import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.lang.Nullable;

/**
 * 检索评估器（eval 雏形的核心）：用黄金集度量 Recall@K——"该召回的在 Top-K 内召回了、不该召回的 没召回"。与具体 Embedding 模型解耦——CI 里用确定性测试替身跑
 * normal 正例，本地用真实 bge-m3 跑全量（含 boundary / adversarial）。
 */
public class RetrievalEvaluator {

  private final VectorStore vectorStore;
  private final double similarityThreshold;
  private final int topK;

  public RetrievalEvaluator(VectorStore vectorStore, double similarityThreshold, int topK) {
    this.vectorStore = vectorStore;
    this.similarityThreshold = similarityThreshold;
    this.topK = topK;
  }

  public List<EvalResult> evaluate(List<GoldenCase> cases) {
    return cases.stream().map(this::evaluateOne).toList();
  }

  private EvalResult evaluateOne(GoldenCase goldenCase) {
    List<Document> results =
        vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(goldenCase.query())
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .build());
    List<String> actualSources =
        results.stream().map(d -> String.valueOf(d.getMetadata().get("source"))).toList();
    boolean pass =
        goldenCase.expectSource() == null
            ? actualSources.isEmpty()
            : actualSources.contains(goldenCase.expectSource());
    return new EvalResult(goldenCase, actualSources, pass);
  }

  /**
   * @param actualSources Top-K 实际命中来源（按相似度降序）；空列表表示未召回任何文档
   */
  public record EvalResult(GoldenCase goldenCase, List<String> actualSources, boolean pass) {

    /** Top-1 命中来源（报告展示用）；{@code null} 表示未召回 */
    public @Nullable String topSource() {
      return actualSources.isEmpty() ? null : actualSources.getFirst();
    }
  }
}
