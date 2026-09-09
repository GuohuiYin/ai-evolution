package com.aievolution.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * W8-2 增量摄入：内容哈希比对 manifest——未变文件零 embedding 调用，变更文件整删重写， 删除文件清理向量。避免每次启动全量重算烧 embedding API。
 *
 * <p>注意：摄入按文件粒度调用 {@code VectorStore.add}（每文件一次），便于按文件记录 chunkIds。
 */
class KnowledgeBaseIngestorIncrementalTest {

  // TokenTextSplitter 默认丢弃短于 minChunkSizeChars 的文本，测试语料必须够长才有分块
  private static final String LONG_A = "茅台 2024 年报内容，酱香白酒酿造工艺。".repeat(30);
  private static final String LONG_B = "平安银行公告内容，业绩说明与风险提示。".repeat(30);

  @TempDir Path knowledgeDir;

  @TempDir Path stateDir;

  private KnowledgeBaseIngestor newIngestor(VectorStore vectorStore) {
    return new KnowledgeBaseIngestor(
        vectorStore,
        800,
        "file:" + knowledgeDir.toAbsolutePath() + "/*",
        stateDir.resolve("manifest.json").toString());
  }

  /** 汇总所有 add 调用中的文档（按文件粒度多次调用）。 */
  private List<Document> allAddedDocs(VectorStore vectorStore, int totalAddCalls) {
    ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
    verify(vectorStore, times(totalAddCalls)).add(captor.capture());
    List<Document> all = new ArrayList<>();
    captor.getAllValues().forEach(all::addAll);
    return all;
  }

  private List<String> idsOf(List<Document> docs, String source) {
    return docs.stream()
        .filter(d -> source.equals(d.getMetadata().get("source")))
        .map(Document::getId)
        .toList();
  }

  @Test
  void secondRunSkipsUnchangedFilesWithoutTouchingVectorStore() throws Exception {
    Files.writeString(knowledgeDir.resolve("a.md"), LONG_A);
    Files.writeString(knowledgeDir.resolve("b.md"), LONG_B);
    VectorStore vectorStore = mock(VectorStore.class);

    newIngestor(vectorStore).run(null); // 首次：两个文件各一次写入
    verify(vectorStore, times(2)).add(anyList());

    newIngestor(vectorStore).run(null); // 二次：全部跳过，零写入零删除
    verify(vectorStore, times(2)).add(anyList());
    verify(vectorStore, never()).delete(anyList());
  }

  @Test
  void changedFileIsReIngestedAndOldChunksDeleted() throws Exception {
    Path target = knowledgeDir.resolve("a.md");
    Files.writeString(target, LONG_A);
    Files.writeString(knowledgeDir.resolve("b.md"), LONG_B);
    VectorStore vectorStore = mock(VectorStore.class);

    newIngestor(vectorStore).run(null);
    List<String> oldIdsOfA = idsOf(allAddedDocs(vectorStore, 2), "a.md");
    assertThat(oldIdsOfA).isNotEmpty();

    Files.writeString(target, "变更后的新内容" + LONG_A);
    newIngestor(vectorStore).run(null);

    // 变更文件的旧向量被清理
    verify(vectorStore).delete(oldIdsOfA);
    // 第二轮只有变更文件重新写入（累计第 3 次 add），且内容只属于 a.md
    List<Document> secondRound = allAddedDocs(vectorStore, 3);
    assertThat(idsOf(secondRound, "b.md")).hasSize(1); // 首轮的 b.md
    List<Document> reIngested =
        secondRound.stream().filter(d -> "a.md".equals(d.getMetadata().get("source"))).toList();
    assertThat(reIngested).isNotEmpty();
    // 分块机制下只有首块含新内容前缀，验证"至少一块携带新内容"即可证明重摄入生效
    assertThat(reIngested).anySatisfy(d -> assertThat(d.getText()).contains("变更后的新内容"));
  }

  @Test
  void removedFileChunksAreDeletedFromVectorStore() throws Exception {
    Path target = knowledgeDir.resolve("a.md");
    Files.writeString(target, LONG_A);
    Files.writeString(knowledgeDir.resolve("b.md"), LONG_B);
    VectorStore vectorStore = mock(VectorStore.class);

    newIngestor(vectorStore).run(null);
    List<String> oldIdsOfA = idsOf(allAddedDocs(vectorStore, 2), "a.md");
    assertThat(oldIdsOfA).isNotEmpty();

    Files.delete(target);
    newIngestor(vectorStore).run(null);

    verify(vectorStore).delete(oldIdsOfA);
    verify(vectorStore, times(2)).add(anyList()); // 无新增/变更，不再写入
  }
}
