package com.aievolution.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

class VectorStoreKnowledgeRetrieverTest {

  private final VectorStore vectorStore = mock(VectorStore.class);

  @Test
  void searchRequestCarriesConfiguredTopKAndThreshold() {
    KnowledgeRetriever retriever = new VectorStoreKnowledgeRetriever(vectorStore, 0.65, 7);

    retriever.retrieve("茅台工艺");

    ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
    verify(vectorStore).similaritySearch(captor.capture());
    SearchRequest request = captor.getValue();
    assertThat(request.getQuery()).isEqualTo("茅台工艺");
    assertThat(request.getTopK()).isEqualTo(7);
    assertThat(request.getSimilarityThreshold()).isEqualTo(0.65);
  }

  @Test
  void knowledgeFilterIsTranslatedIntoSearchRequest() {
    KnowledgeRetriever retriever = new VectorStoreKnowledgeRetriever(vectorStore, 0.5, 5);

    retriever.retrieve("年报", new KnowledgeFilter("report", "2024-12-31"));

    ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
    verify(vectorStore).similaritySearch(captor.capture());
    // 元数据过滤：docType 与 asOf 都必须进入过滤表达式
    String filter = String.valueOf(captor.getValue().getFilterExpression());
    assertThat(filter)
        .contains("docType")
        .contains("report")
        .contains("asOf")
        .contains("2024-12-31");
  }

  private static Document doc(String id) {
    return Document.builder()
        .id(id)
        .text("text-" + id)
        .metadata(Map.of("source", id + ".md"))
        .build();
  }

  @Test
  void hybridOffIgnoresSparseRecallCompletely() {
    SparseRecall sparse = mock(SparseRecall.class);
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc("d1")));
    KnowledgeRetriever retriever =
        new VectorStoreKnowledgeRetriever(vectorStore, 0.5, 5, Optional.of(sparse), false, 20, 60);

    List<Document> hits = retriever.retrieve("茅台工艺");

    assertThat(hits).extracting(Document::getId).containsExactly("d1");
    verifyNoInteractions(sparse);
  }

  @Test
  void hybridOnFusesBothPathsAndCapsAtTopK() {
    SparseRecall sparse = mock(SparseRecall.class);
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(doc("d1"), doc("d2"), doc("d3")));
    when(sparse.recall(eq("12987 工艺"), any(), eq(20))).thenReturn(List.of(doc("s1"), doc("d2")));
    KnowledgeRetriever retriever =
        new VectorStoreKnowledgeRetriever(vectorStore, 0.5, 2, Optional.of(sparse), true, 20, 60);

    List<Document> hits = retriever.retrieve("12987 工艺");

    // d2 双路命中被 RRF 提升到第一；最终结果截到 topK=2（线上返回口径不变）
    assertThat(hits).extracting(Document::getId).containsExactly("d2", "d1");
    // dense 路改用放大召回口径 recall-top-k
    ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
    verify(vectorStore).similaritySearch(captor.capture());
    assertThat(captor.getValue().getTopK()).isEqualTo(20);
  }

  @Test
  void hybridOnWithoutSparseBeanFallsBackToSingleStage() {
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc("d1")));
    KnowledgeRetriever retriever =
        new VectorStoreKnowledgeRetriever(vectorStore, 0.5, 5, Optional.empty(), true, 20, 60);

    List<Document> hits = retriever.retrieve("茅台工艺");

    assertThat(hits).extracting(Document::getId).containsExactly("d1");
    ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
    verify(vectorStore).similaritySearch(captor.capture());
    assertThat(captor.getValue().getTopK()).isEqualTo(5);
  }

  @Test
  void rerankOnUsesRecallPoolAndReturnsRerankerOrder() {
    RerankerClient reranker = mock(RerankerClient.class);
    List<Document> pool = List.of(doc("d1"), doc("d2"), doc("d3"));
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(pool);
    when(reranker.rerank(eq("茅台工艺"), eq(pool), eq(3)))
        .thenReturn(
            List.of(doc("d2").mutate().score(0.9).build(), doc("d1").mutate().score(0.7).build()));
    KnowledgeRetriever retriever =
        new VectorStoreKnowledgeRetriever(
            vectorStore, 0.5, 5, Optional.empty(), false, 20, 60, Optional.of(reranker), true, 3);

    List<Document> hits = retriever.retrieve("茅台工艺");

    // 精排后顺序与分数由 reranker 决定；召回侧用放大口径
    assertThat(hits).extracting(Document::getId).containsExactly("d2", "d1");
    assertThat(hits.getFirst().getScore()).isEqualTo(0.9);
    ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
    verify(vectorStore).similaritySearch(captor.capture());
    assertThat(captor.getValue().getTopK()).isEqualTo(20);
  }

  @Test
  void rerankFailureDegradesToRecallPoolCappedAtTopN() {
    RerankerClient reranker = mock(RerankerClient.class);
    List<Document> pool = List.of(doc("d1"), doc("d2"), doc("d3"), doc("d4"));
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(pool);
    when(reranker.rerank(any(), any(), eq(3)))
        .thenThrow(new RerankerClient.RerankException("供应商 5xx", null));
    KnowledgeRetriever retriever =
        new VectorStoreKnowledgeRetriever(
            vectorStore, 0.5, 5, Optional.empty(), false, 20, 60, Optional.of(reranker), true, 3);

    List<Document> hits = retriever.retrieve("茅台工艺");

    // 故障降级回召回结果（截 topN），不报错、不穿底
    assertThat(hits).extracting(Document::getId).containsExactly("d1", "d2", "d3");
  }

  @Test
  void emptyRecallSkipsRerankCall() {
    RerankerClient reranker = mock(RerankerClient.class);
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
    KnowledgeRetriever retriever =
        new VectorStoreKnowledgeRetriever(
            vectorStore, 0.5, 5, Optional.empty(), false, 20, 60, Optional.of(reranker), true, 5);

    assertThat(retriever.retrieve("茅台工艺")).isEmpty();
    verifyNoInteractions(reranker);
  }
}
