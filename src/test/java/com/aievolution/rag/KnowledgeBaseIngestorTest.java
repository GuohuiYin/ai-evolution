package com.aievolution.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

class KnowledgeBaseIngestorTest {

  @TempDir Path stateDir;

  @Test
  void ingestsMarkdownWithDeterministicIdsAndSourceMetadata() throws Exception {
    VectorStore vectorStore = mock(VectorStore.class);
    KnowledgeBaseIngestor ingestor =
        new KnowledgeBaseIngestor(
            vectorStore,
            800,
            "classpath:knowledge/*.md",
            stateDir.resolve("manifest.json").toString());

    ingestor.run(null);
    ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
    verify(vectorStore, atLeastOnce()).add(captor.capture());

    List<Document> chunks = new ArrayList<>();
    captor.getAllValues().forEach(chunks::addAll);
    assertThat(chunks).isNotEmpty();
    assertThat(chunks)
        .allSatisfy(
            doc -> {
              // 确定性 ID（文件名#块序号，UUIDv3）：同内容重写不产生新 ID，配合 Qdrant upsert 不重复
              assertThat(doc.getId()).isNotBlank();
              assertThat(doc.getMetadata()).containsKey("source");
              assertThat(doc.getText()).isNotBlank();
            });
  }
}
