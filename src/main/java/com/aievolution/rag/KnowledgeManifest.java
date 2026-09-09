package com.aievolution.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库摄入清单（W8-2 增量摄入）：记录每个源文件的内容哈希与其向量块 ID 集合。
 *
 * <p>职责单一：清单的读、写、增删。比对与决策逻辑在 {@link KnowledgeBaseIngestor}。
 *
 * <p>持久化为 JSON 文件（默认 {@code build/knowledge-manifest.json}，不入 git）。文件缺失或损坏时
 * 视为空清单——语义等价于"全部重新摄入"，保证可从零自愈。
 */
class KnowledgeManifest {

  /** 单文件条目：内容 SHA-256 + 该文件所有向量块 ID（用于变更/删除时清理）。 */
  record Entry(String sha256, List<String> chunkIds) {}

  private final Path file;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Map<String, Entry> entries = new LinkedHashMap<>();

  KnowledgeManifest(Path file) {
    this.file = file;
    load();
  }

  private void load() {
    if (!Files.exists(file)) {
      return;
    }
    try {
      Map<String, Map<String, Object>> raw = objectMapper.readValue(file.toFile(), Map.class);
      raw.forEach(
          (name, v) ->
              entries.put(
                  name,
                  new Entry(
                      String.valueOf(v.get("sha256")),
                      ((List<?>) v.get("chunkIds")).stream().map(String::valueOf).toList())));
    } catch (Exception e) {
      // 损坏清单 = 空清单：不抛错阻断启动，让摄入器走全量重建
      entries.clear();
    }
  }

  void put(String source, Entry entry) {
    entries.put(source, entry);
  }

  void remove(String source) {
    entries.remove(source);
  }

  /** 返回不可变快照，避免调用方意外污染内部状态。 */
  Map<String, Entry> entries() {
    return Map.copyOf(entries);
  }

  void save() {
    try {
      Files.createDirectories(file.getParent());
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), entries);
    } catch (IOException e) {
      throw new IllegalStateException("知识库摄入清单写入失败: " + file, e);
    }
  }
}
