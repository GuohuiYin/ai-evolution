package com.aievolution.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
}
