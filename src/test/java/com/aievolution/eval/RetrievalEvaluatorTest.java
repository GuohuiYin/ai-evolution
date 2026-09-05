package com.aievolution.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

class RetrievalEvaluatorTest {

  private static final int TOP_K = 5;

  private final VectorStore vectorStore = mock(VectorStore.class);

  private static Document doc(String source) {
    return new Document("id-" + source, "内容", Map.of("source", source));
  }

  @Test
  void passWhenExpectedSourceAppearsInTopK() {
    // Recall@K 语义：期望来源不在 Top-1、但在 Top-K 内，仍算召回
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(doc("catl.md"), doc("maotai.md")));

    List<RetrievalEvaluator.EvalResult> results =
        new RetrievalEvaluator(vectorStore, 0.5, TOP_K)
            .evaluate(List.of(new GoldenCase("茅台工艺", "maotai.md")));

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
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(doc("catl.md")));

    List<RetrievalEvaluator.EvalResult> results =
        new RetrievalEvaluator(vectorStore, 0.5, TOP_K)
            .evaluate(List.of(new GoldenCase("茅台工艺", "maotai.md")));

    assertThat(results).singleElement().satisfies(r -> assertThat(r.pass()).isFalse());
  }

  @Test
  void passNegativeCaseWhenNothingRetrieved() {
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

    List<RetrievalEvaluator.EvalResult> results =
        new RetrievalEvaluator(vectorStore, 0.5, TOP_K)
            .evaluate(List.of(new GoldenCase("今天天气怎么样", null)));

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
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(doc("maotai.md")));

    List<RetrievalEvaluator.EvalResult> results =
        new RetrievalEvaluator(vectorStore, 0.5, TOP_K)
            .evaluate(List.of(new GoldenCase("比亚迪的刀片电池技术参数", null)));

    assertThat(results).singleElement().satisfies(r -> assertThat(r.pass()).isFalse());
  }

  @Test
  void searchRequestCarriesConfiguredTopK() {
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

    new RetrievalEvaluator(vectorStore, 0.5, TOP_K).evaluate(List.of(new GoldenCase("任意问题", null)));

    ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
    org.mockito.Mockito.verify(vectorStore).similaritySearch(captor.capture());
    assertThat(captor.getValue().getTopK()).isEqualTo(TOP_K);
  }
}
