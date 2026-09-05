package com.aievolution.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

class PdfIngestionTest {

  @Test
  void recursiveLocationSkipsDirectoriesAndMetaSidecars() throws Exception {
    // 回归：classpath:** 递归 glob 会匹配到子目录本身（"Is a directory" 崩溃），必须跳过
    VectorStore vectorStore = mock(VectorStore.class);
    KnowledgeBaseIngestor ingestor =
        new KnowledgeBaseIngestor(vectorStore, 800, "classpath:knowledge-test/**/*");

    ingestor.run(null);

    ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
    verify(vectorStore).add(captor.capture());
    assertThat(captor.getValue().stream().map(d -> d.getMetadata().get("source")))
        .contains("test-announcement.pdf", "research-note.md")
        .noneMatch(s -> String.valueOf(s).endsWith(".meta.json"));
  }

  @Test
  void ingestsPdfWithMetadataSidecar() throws Exception {
    VectorStore vectorStore = mock(VectorStore.class);
    KnowledgeBaseIngestor ingestor =
        new KnowledgeBaseIngestor(vectorStore, 800, "classpath:knowledge-test/*.pdf");

    ingestor.run(null);

    ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
    verify(vectorStore).add(captor.capture());
    // 元数据规范：source/docType/asOf 三件套进 payload（红线 03 的结构化落地）
    assertThat(captor.getValue())
        .isNotEmpty()
        .allSatisfy(
            doc -> {
              assertThat(doc.getMetadata())
                  .containsEntry("source", "test-announcement.pdf")
                  .containsEntry("docType", "announcement")
                  .containsEntry("asOf", "2026-09-05");
              // PDFBox 抽取的文本含不规则空白，断言前先归一化
              assertThat(doc.getText().replaceAll("\\s+", " ")).contains("AI Evolution");
            });
  }
}
