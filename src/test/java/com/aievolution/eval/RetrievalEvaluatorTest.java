package com.aievolution.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

class RetrievalEvaluatorTest {

  private final VectorStore vectorStore = mock(VectorStore.class);

  @Test
  void passWhenActualSourceMatchesExpected() {
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(new Document("id", "内容", Map.of("source", "maotai.md"))));

    List<RetrievalEvaluator.EvalResult> results =
        new RetrievalEvaluator(vectorStore, 0.5)
            .evaluate(List.of(new GoldenCase("茅台工艺", "maotai.md")));

    assertThat(results)
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.pass()).isTrue();
              assertThat(r.actualSource()).isEqualTo("maotai.md");
            });
  }

  @Test
  void passNegativeCaseWhenNothingRetrieved() {
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

    List<RetrievalEvaluator.EvalResult> results =
        new RetrievalEvaluator(vectorStore, 0.5).evaluate(List.of(new GoldenCase("今天天气怎么样", null)));

    assertThat(results)
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.pass()).isTrue();
              assertThat(r.actualSource()).isNull();
            });
  }

  @Test
  void failWhenRetrievalHitsWrongSource() {
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(new Document("id", "内容", Map.of("source", "catl.md"))));

    List<RetrievalEvaluator.EvalResult> results =
        new RetrievalEvaluator(vectorStore, 0.5)
            .evaluate(List.of(new GoldenCase("茅台工艺", "maotai.md")));

    assertThat(results).singleElement().satisfies(r -> assertThat(r.pass()).isFalse());
  }
}
