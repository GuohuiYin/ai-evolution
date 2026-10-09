package com.aievolution.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aievolution.rag.KnowledgeRetriever;
import com.aievolution.rag.PassThroughQueryRewriter;
import com.aievolution.rag.QueryRewriter;
import com.aievolution.rag.RetrievalEvidence;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

/**
 * 检索评估器单测。W15 #2 起 eval 链路对齐对话通路——每查询先经 {@link QueryRewriter}（空历史）
 * 改写再检索：评的就是用的（含改写层的系统检索能力，不再是裸检索器）。
 *
 * <p>W16 #1 起走 {@link KnowledgeRetriever#retrieveWithEvidence(String)} 取证口径：逐案 dense topScore
 * 随结果外暴露——阈值校准的分布证据由 eval 报告承载。
 */
class RetrievalEvaluatorTest {

  private final KnowledgeRetriever knowledgeRetriever = mock(KnowledgeRetriever.class);
  private final RetrievalEvaluator evaluator =
      new RetrievalEvaluator(knowledgeRetriever, new PassThroughQueryRewriter());

  private static Document doc(String source) {
    return new Document("id-" + source, "内容", Map.of("source", source));
  }

  private static RetrievalEvidence evidenceOf(Document... docs) {
    List<Document> hits = List.of(docs);
    Double denseTop = hits.isEmpty() ? null : hits.getFirst().getScore();
    return new RetrievalEvidence(hits, denseTop);
  }

  @Test
  void passWhenExpectedSourceAppearsInTopK() {
    // Recall@K 语义：期望来源不在 Top-1、但在 Top-K 内，仍算召回
    when(knowledgeRetriever.retrieveWithEvidence("茅台工艺"))
        .thenReturn(evidenceOf(doc("catl.md"), doc("maotai.md")));

    List<RetrievalEvaluator.EvalResult> results =
        evaluator.evaluate(List.of(new GoldenCase("茅台工艺", "maotai.md")));

    assertThat(results)
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.pass()).isTrue();
              assertThat(r.topSource()).isEqualTo("catl.md");
              assertThat(r.actualSources()).containsExactly("catl.md", "maotai.md");
            });
  }

  @Test
  void failWhenExpectedSourceAbsentFromTopK() {
    when(knowledgeRetriever.retrieveWithEvidence("茅台工艺")).thenReturn(evidenceOf(doc("catl.md")));

    List<RetrievalEvaluator.EvalResult> results =
        evaluator.evaluate(List.of(new GoldenCase("茅台工艺", "maotai.md")));

    assertThat(results).singleElement().satisfies(r -> assertThat(r.pass()).isFalse());
  }

  @Test
  void passNegativeCaseWhenNothingRetrieved() {
    when(knowledgeRetriever.retrieveWithEvidence("今天天气怎么样")).thenReturn(evidenceOf());

    List<RetrievalEvaluator.EvalResult> results =
        evaluator.evaluate(List.of(new GoldenCase("今天天气怎么样", null)));

    assertThat(results)
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.pass()).isTrue();
              assertThat(r.topSource()).isNull();
            });
  }

  @Test
  void failNegativeCaseWhenSomethingRetrieved() {
    when(knowledgeRetriever.retrieveWithEvidence("比亚迪的刀片电池技术参数"))
        .thenReturn(evidenceOf(doc("maotai.md")));

    List<RetrievalEvaluator.EvalResult> results =
        evaluator.evaluate(List.of(new GoldenCase("比亚迪的刀片电池技术参数", null)));

    assertThat(results).singleElement().satisfies(r -> assertThat(r.pass()).isFalse());
  }

  @Test
  void queryIsRewrittenBeforeRetrieval() {
    // W15 #2 核心契约：语料指代查询经改写展开锚定词后再检索；生效查询入结果备查
    QueryRewriter rewriter = mock(QueryRewriter.class);
    when(rewriter.rewrite(anyString(), anyList())).thenReturn("贵州茅台和宁德时代谁的品牌壁垒更厚");
    RetrievalEvaluator rewritingEvaluator = new RetrievalEvaluator(knowledgeRetriever, rewriter);
    when(knowledgeRetriever.retrieveWithEvidence("贵州茅台和宁德时代谁的品牌壁垒更厚"))
        .thenReturn(evidenceOf(doc("maotai.md")));

    List<RetrievalEvaluator.EvalResult> results =
        rewritingEvaluator.evaluate(List.of(new GoldenCase("这两家公司里谁的品牌壁垒更厚", "maotai.md")));

    assertThat(results)
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.pass()).isTrue();
              assertThat(r.effectiveQuery()).isEqualTo("贵州茅台和宁德时代谁的品牌壁垒更厚");
            });
    // 检索器收到的必须是改写后的查询，不是原始查询
    verify(knowledgeRetriever).retrieveWithEvidence("贵州茅台和宁德时代谁的品牌壁垒更厚");
  }

  @Test
  void resultCarriesDenseTopScoreFromEvidence() {
    // W16 #1 核心契约：逐案 dense topScore 随结果外暴露——阈值 0.5 的分布校准靠它
    Document hit =
        Document.builder()
            .id("id-maotai.md")
            .text("内容")
            .metadata(Map.of("source", "maotai.md"))
            .score(0.83)
            .build();
    when(knowledgeRetriever.retrieveWithEvidence("茅台工艺"))
        .thenReturn(new RetrievalEvidence(List.of(hit), 0.83));

    List<RetrievalEvaluator.EvalResult> results =
        evaluator.evaluate(List.of(new GoldenCase("茅台工艺", "maotai.md")));

    assertThat(results).singleElement().satisfies(r -> assertThat(r.topScore()).isEqualTo(0.83));
  }

  @Test
  void topScoreIsNullWhenNothingRetrieved() {
    // 未召回时分数缺席——缺席本身即拒答判罚依据，报告段据此分组
    when(knowledgeRetriever.retrieveWithEvidence("今天天气怎么样")).thenReturn(evidenceOf());

    List<RetrievalEvaluator.EvalResult> results =
        evaluator.evaluate(List.of(new GoldenCase("今天天气怎么样", null)));

    assertThat(results).singleElement().satisfies(r -> assertThat(r.topScore()).isNull());
  }
}
