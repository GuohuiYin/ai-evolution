package com.aievolution.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.aievolution.rag.SparseVectorWriter.ChunkText;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * W16 #2 A3-lite 摄入侧契约：sparse impl 切换入签名判变键（换实现即全量重建）； sparse 在场时摄入器把 chunk 文本交给 {@link
 * SparseVectorWriter}，avgdl 为全语料 词项均值（留存文件统计自 manifest tokenSum，新文件现算）——BM25 长度归一的口径锚点。
 */
class KnowledgeBaseIngestorSparseTest {

  private static final String LONG_A = "茅台 2024 年报内容，酱香白酒酿造工艺。".repeat(30);
  private static final String LONG_B = "平安银行公告内容，业绩说明与风险提示。".repeat(30);

  @TempDir Path knowledgeDir;

  @TempDir Path stateDir;

  /** 记录型假写入器：tokenCount 委托真编码器（统计口径一致），write 只记账。 */
  private static class FakeSparseVectorWriter implements SparseVectorWriter {
    private final Bm25Encoder encoder = new Bm25Encoder(1.5, 0.75);
    final List<List<ChunkText>> writes = new ArrayList<>();
    final List<Double> avgLens = new ArrayList<>();
    final List<String> countedTexts = new ArrayList<>();

    @Override
    public void ensureSchema() {}

    @Override
    public int tokenCount(String text) {
      countedTexts.add(text);
      return encoder.termCount(text);
    }

    @Override
    public void write(List<ChunkText> chunks, double avgDocLength) {
      writes.add(chunks);
      avgLens.add(avgDocLength);
    }
  }

  private KnowledgeBaseIngestor newIngestor(
      VectorStore vectorStore, Optional<SparseVectorWriter> writer, String sparseImpl) {
    return new KnowledgeBaseIngestor(
        vectorStore,
        800,
        "test-embedding-model",
        "test-collection",
        "file:" + knowledgeDir.toAbsolutePath() + "/*",
        stateDir.resolve("manifest.json").toString(),
        writer,
        sparseImpl);
  }

  @Test
  void sparseImplSwitchTriggersFullReingest() throws Exception {
    // 签名判变键扩 sparse 位：matchtext → bm25 切换后文件 SHA 未变，仍必须全量重建
    // （旧块没有 BM25 稀疏向量，拿旧块服务等于 sparse 路空转）
    Files.writeString(knowledgeDir.resolve("a.md"), LONG_A);
    Files.writeString(knowledgeDir.resolve("b.md"), LONG_B);
    VectorStore vectorStore = mock(VectorStore.class);
    FakeSparseVectorWriter writer = new FakeSparseVectorWriter();

    newIngestor(vectorStore, Optional.empty(), "matchtext").run(null);
    ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
    verify(vectorStore, times(2)).add(captor.capture());
    List<String> oldIdsOfA =
        captor.getAllValues().stream()
            .flatMap(List::stream)
            .filter(d -> "a.md".equals(d.getMetadata().get("source")))
            .map(Document::getId)
            .toList();

    newIngestor(vectorStore, Optional.of(writer), "bm25").run(null);

    verify(vectorStore).delete(oldIdsOfA);
    verify(vectorStore, times(4)).add(anyList());
    assertThat(writer.writes).hasSize(2); // 两个文件各一次稀疏写入
  }

  @Test
  void writerReceivesAllChunksWithCorpusWideAvgdl() throws Exception {
    Files.writeString(knowledgeDir.resolve("a.md"), LONG_A);
    Files.writeString(knowledgeDir.resolve("b.md"), LONG_B);
    VectorStore vectorStore = mock(VectorStore.class);
    FakeSparseVectorWriter writer = new FakeSparseVectorWriter();

    newIngestor(vectorStore, Optional.of(writer), "bm25").run(null);

    // 全部 chunk 进写入器，且 ID 与 dense 侧一致（同 point 挂稀疏向量）
    ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
    verify(vectorStore, times(2)).add(captor.capture());
    List<String> denseIds =
        captor.getAllValues().stream().flatMap(List::stream).map(Document::getId).toList();
    List<String> sparseIds =
        writer.writes.stream().flatMap(List::stream).map(ChunkText::id).toList();
    assertThat(sparseIds).containsExactlyInAnyOrderElementsOf(denseIds);

    // avgdl = 全语料词项数 / 全语料块数（自洽口径：tokenCount 输入即 chunk 文本）
    long totalTerms = writer.countedTexts.stream().mapToInt(writer.encoder::termCount).sum();
    double expectedAvg = (double) totalTerms / sparseIds.size();
    assertThat(writer.avgLens).allSatisfy(avg -> assertThat(avg).isEqualTo(expectedAvg));
  }

  @Test
  void retainedFileStatsJoinAvgdlOnIncrementalRun() throws Exception {
    // 增量场景：第二轮新增文件时，留存文件的 tokenSum 必须计入 avgdl（manifest 持久化的意义）
    Files.writeString(knowledgeDir.resolve("a.md"), LONG_A);
    VectorStore vectorStore = mock(VectorStore.class);
    FakeSparseVectorWriter writer1 = new FakeSparseVectorWriter();
    newIngestor(vectorStore, Optional.of(writer1), "bm25").run(null);

    Files.writeString(knowledgeDir.resolve("b.md"), LONG_B);
    FakeSparseVectorWriter writer2 = new FakeSparseVectorWriter();
    newIngestor(vectorStore, Optional.of(writer2), "bm25").run(null);

    // 第二轮只写 b.md 的稀疏向量，但 avgdl 必须是 a+b 全语料口径（第一轮实录数据算期望）
    assertThat(writer2.writes).hasSize(1);
    long aTerms = writer1.countedTexts.stream().mapToInt(writer1.encoder::termCount).sum();
    long aChunks = writer1.writes.stream().mapToInt(List::size).sum();
    long bTerms = writer2.countedTexts.stream().mapToInt(writer2.encoder::termCount).sum();
    long bChunks = writer2.writes.getFirst().size();
    double expectedCorpusAvg = (double) (aTerms + bTerms) / (aChunks + bChunks);
    assertThat(writer2.avgLens.getFirst()).isEqualTo(expectedCorpusAvg);
  }

  @Test
  void matchtextModeLeavesNoTokenStatsInManifest() throws Exception {
    Files.writeString(knowledgeDir.resolve("a.md"), LONG_A);
    VectorStore vectorStore = mock(VectorStore.class);

    newIngestor(vectorStore, Optional.empty(), "matchtext").run(null);

    KnowledgeManifest manifest = new KnowledgeManifest(stateDir.resolve("manifest.json"));
    // sparse 关闭态：tokenSum 缺省（null）——统计原料只在 BM25 形态下产生，不留死字段
    assertThat(manifest.entries().get("a.md").tokenSum()).isNull();
  }
}
