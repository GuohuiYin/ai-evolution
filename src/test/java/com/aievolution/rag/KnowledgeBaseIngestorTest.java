package com.aievolution.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

class KnowledgeBaseIngestorTest {

  @Test
  void ingestsMarkdownWithDeterministicIdsAndSourceMetadata() throws Exception {
    VectorStore vectorStore = mock(VectorStore.class);
    KnowledgeBaseIngestor ingestor = new KnowledgeBaseIngestor(vectorStore);

    ingestor.run(null);
    ArgumentCaptor<List<Document>> first = ArgumentCaptor.forClass(List.class);
    verify(vectorStore).add(first.capture());

    List<Document> chunks = first.getValue();
    assertThat(chunks).isNotEmpty();
    assertThat(chunks)
        .allSatisfy(
            doc -> {
              assertThat(doc.getId()).isNotBlank();
              assertThat(doc.getMetadata()).containsKey("source");
              assertThat(doc.getText()).isNotBlank();
            });

    // 幂等性：再次摄入，文档 ID 完全一致（配合 Qdrant upsert，重复启动不产生重复向量）
    ingestor.run(null);
    ArgumentCaptor<List<Document>> second = ArgumentCaptor.forClass(List.class);
    verify(vectorStore, times(2)).add(second.capture());
    assertThat(second.getValue().stream().map(Document::getId).toList())
        .containsExactlyElementsOf(chunks.stream().map(Document::getId).toList());
  }
}
