package com.aievolution.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KnowledgeManifestTest {

  @TempDir Path tempDir;

  @Test
  void roundTripPreservesEntries() {
    Path file = tempDir.resolve("manifest.json");
    KnowledgeManifest manifest = new KnowledgeManifest(file);
    manifest.put("a.pdf", new KnowledgeManifest.Entry("hash-a", List.of("id-1", "id-2")));
    manifest.put("b.md", new KnowledgeManifest.Entry("hash-b", List.of("id-3")));
    manifest.save();

    KnowledgeManifest reloaded = new KnowledgeManifest(file);
    assertThat(reloaded.entries()).containsOnlyKeys("a.pdf", "b.md");
    assertThat(reloaded.entries().get("a.pdf").sha256()).isEqualTo("hash-a");
    assertThat(reloaded.entries().get("a.pdf").chunkIds()).containsExactly("id-1", "id-2");
  }

  @Test
  void missingFileYieldsEmptyManifest() {
    KnowledgeManifest manifest = new KnowledgeManifest(tempDir.resolve("not-exists.json"));
    assertThat(manifest.entries()).isEmpty();
  }

  @Test
  void corruptedFileYieldsEmptyManifestForRebuild() throws Exception {
    Path file = tempDir.resolve("manifest.json");
    Files.writeString(file, "{这不是合法JSON");
    KnowledgeManifest manifest = new KnowledgeManifest(file);
    assertThat(manifest.entries()).as("损坏的清单应视为空，触发全量重建").isEmpty();
  }

  @Test
  void removeDropsEntry() {
    KnowledgeManifest manifest = new KnowledgeManifest(tempDir.resolve("manifest.json"));
    manifest.put("a.pdf", new KnowledgeManifest.Entry("h", List.of("id-1")));
    manifest.remove("a.pdf");
    assertThat(manifest.entries()).isEmpty();
  }

  @Test
  void entriesViewIsImmutableSnapshot() {
    KnowledgeManifest manifest = new KnowledgeManifest(tempDir.resolve("manifest.json"));
    manifest.put("a.pdf", new KnowledgeManifest.Entry("h", List.of("id-1")));
    Map<String, KnowledgeManifest.Entry> view = manifest.entries();
    // 不可变：外部修改直接抛错，内部状态不受影响
    org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class, view::clear);
    assertThat(manifest.entries()).containsOnlyKeys("a.pdf");
  }

  @Test
  void roundTripPreservesChunkSignature() {
    Path file = tempDir.resolve("manifest.json");
    KnowledgeManifest manifest = new KnowledgeManifest(file);
    manifest.chunkSignature("sig-800");
    manifest.put("a.md", new KnowledgeManifest.Entry("h", List.of("id-1")));
    manifest.save();

    KnowledgeManifest reloaded = new KnowledgeManifest(file);
    assertThat(reloaded.chunkSignature()).isEqualTo("sig-800");
    assertThat(reloaded.entries()).containsOnlyKeys("a.md");
  }

  @Test
  void legacyFlatFormatWithoutEntriesNodeYieldsEmptyForRebuild() throws Exception {
    // W8-2 旧格式（顶层即文件条目、无分块签名）：视为空清单全量重建，签名置空触发判变
    Path file = tempDir.resolve("manifest.json");
    Files.writeString(file, "{\"a.md\":{\"sha256\":\"h\",\"chunkIds\":[\"id-1\"]}}");
    KnowledgeManifest manifest = new KnowledgeManifest(file);
    assertThat(manifest.entries()).isEmpty();
    assertThat(manifest.chunkSignature()).isNull();
  }
}
