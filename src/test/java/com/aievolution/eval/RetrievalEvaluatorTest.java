package com.aievolution.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aievolution.rag.KnowledgeRetriever;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class RetrievalEvaluatorTest {

  private final KnowledgeRetriever knowledgeRetriever = mock(KnowledgeRetriever.class);
  private final RetrievalEvaluator evaluator = new RetrievalEvaluator(knowledgeRetriever);

  private static Document doc(String source) {
    return new Document("id-" + source, "内容", Map.of("source", source));
  }

  @Test
  void passWhenExpectedSourceAppearsInTopK() {
    // Recall@K 语义：期望来源不在 Top-1、但在 Top-K 内，仍算召回
    when(knowledgeRetriever.retrieve("茅台工艺")).thenReturn(List.of(doc("catl.md"), doc("maotai.md")));

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
    when(knowledgeRetriever.retrieve("茅台工艺")).thenReturn(List.of(doc("catl.md")));

    List<RetrievalEvaluator.EvalResult> results =
        evaluator.evaluate(List.of(new GoldenCase("茅台工艺", "maotai.md")));

    assertThat(results).singleElement().satisfies(r -> assertThat(r.pass()).isFalse());
  }

  @Test
  void passNegativeCaseWhenNothingRetrieved() {
    when(knowledgeRetriever.retrieve("今天天气怎么样")).thenReturn(List.of());

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
    when(knowledgeRetriever.retrieve("比亚迪的刀片电池技术参数")).thenReturn(List.of(doc("maotai.md")));

    List<RetrievalEvaluator.EvalResult> results =
        evaluator.evaluate(List.of(new GoldenCase("比亚迪的刀片电池技术参数", null)));

    assertThat(results).singleElement().satisfies(r -> assertThat(r.pass()).isFalse());
  }
}
