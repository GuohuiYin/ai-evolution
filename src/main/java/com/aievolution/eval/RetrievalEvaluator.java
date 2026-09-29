package com.aievolution.eval;

import com.aievolution.rag.KnowledgeRetriever;
import com.aievolution.rag.QueryRewriter;
import java.util.List;
import org.springframework.lang.Nullable;

/**
 * 检索评估器（eval 雏形的核心）：用黄金集度量 Recall@K——"该召回的在 Top-K 内召回了、不该召回的 没召回"。检索走与线上一致的 {@link
 * KnowledgeRetriever}——评的就是用的。
 *
 * <p>W15 #2 起每查询先经 {@link QueryRewriter}（空历史）改写再检索：eval 链路对齐对话通路，
 * 度量的是含改写层的系统检索能力。生效查询记入结果——语料指代用例的改写证据随报告可回放。
 */
public class RetrievalEvaluator {

  private final KnowledgeRetriever knowledgeRetriever;
  private final QueryRewriter queryRewriter;

  public RetrievalEvaluator(KnowledgeRetriever knowledgeRetriever, QueryRewriter queryRewriter) {
    this.knowledgeRetriever = knowledgeRetriever;
    this.queryRewriter = queryRewriter;
  }

  public List<EvalResult> evaluate(List<GoldenCase> cases) {
    return cases.stream().map(this::evaluateOne).toList();
  }

  private EvalResult evaluateOne(GoldenCase goldenCase) {
    // 单轮黄金集无会话历史，空列表入参——与对话通路首轮同形
    String effectiveQuery = queryRewriter.rewrite(goldenCase.query(), List.of());
    List<String> actualSources =
        knowledgeRetriever.retrieve(effectiveQuery).stream()
            .map(d -> String.valueOf(d.getMetadata().get("source")))
            .toList();
    boolean pass =
        goldenCase.expectSources() == null
            ? actualSources.isEmpty()
            : actualSources.stream().anyMatch(goldenCase.expectSources()::contains);
    return new EvalResult(goldenCase, effectiveQuery, actualSources, pass);
  }

  /**
   * @param effectiveQuery 实际用于检索的查询（改写后；未改写时与原查询相同）
   * @param actualSources Top-K 实际命中来源（按相似度降序）；空列表表示未召回任何文档
   */
  public record EvalResult(
      GoldenCase goldenCase, String effectiveQuery, List<String> actualSources, boolean pass) {

    /** Top-1 命中来源（报告展示用）；{@code null} 表示未召回 */
    public @Nullable String topSource() {
      return actualSources.isEmpty() ? null : actualSources.getFirst();
    }
  }
}
