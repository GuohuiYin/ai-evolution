package com.aievolution.eval;

import com.aievolution.rag.KnowledgeRetriever;
import java.util.List;
import org.springframework.lang.Nullable;

/**
 * 检索评估器（eval 雏形的核心）：用黄金集度量 Recall@K——"该召回的在 Top-K 内召回了、不该召回的 没召回"。检索走与线上一致的 {@link
 * KnowledgeRetriever}——评的就是用的。
 */
public class RetrievalEvaluator {

  private final KnowledgeRetriever knowledgeRetriever;

  public RetrievalEvaluator(KnowledgeRetriever knowledgeRetriever) {
    this.knowledgeRetriever = knowledgeRetriever;
  }

  public List<EvalResult> evaluate(List<GoldenCase> cases) {
    return cases.stream().map(this::evaluateOne).toList();
  }

  private EvalResult evaluateOne(GoldenCase goldenCase) {
    List<String> actualSources =
        knowledgeRetriever.retrieve(goldenCase.query()).stream()
            .map(d -> String.valueOf(d.getMetadata().get("source")))
            .toList();
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
