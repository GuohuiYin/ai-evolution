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
 * <p>W13-1 增补：顶层携带 {@code chunkSignature}（分块参数哈希）。chunk-size 等分块参数变更后 文件内容 SHA
 * 不变，单靠条目哈希会拿旧块服务——签名不符即全量重建（W13 #1 对照实验实测坑）。
 *
 * <p>持久化为 JSON 文件（默认 {@code build/knowledge-manifest.json}，不入 git）。文件缺失、损坏或 为旧版平铺格式（无 entries
 * 节点）时视为空清单——语义等价于"全部重新摄入"，保证可从零自愈。
 */
class KnowledgeManifest {

  /** 单文件条目：内容 SHA-256 + 该文件所有向量块 ID（用于变更/删除时清理）。 */
  record Entry(String sha256, List<String> chunkIds) {}

  private final Path file;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Map<String, Entry> entries = new LinkedHashMap<>();
  private String chunkSignature;

  KnowledgeManifest(Path file) {
    this.file = file;
    load();
  }

  private void load() {
    if (!Files.exists(file)) {
      return;
    }
    try {
      Map<String, Object> raw = objectMapper.readValue(file.toFile(), Map.class);
      Object node = raw.get("entries");
      if (!(node instanceof Map)) {
        // 旧版平铺格式或缺节点：同损坏处理，全量重建自愈
        throw new IllegalStateException("清单缺少 entries 节点");
      }
      chunkSignature =
          raw.get("chunkSignature") == null ? null : String.valueOf(raw.get("chunkSignature"));
      ((Map<String, Map<String, Object>>) node)
          .forEach(
              (name, v) ->
                  entries.put(
                      name,
                      new Entry(
                          String.valueOf(v.get("sha256")),
                          ((List<?>) v.get("chunkIds")).stream().map(String::valueOf).toList())));
    } catch (Exception e) {
      // 损坏清单 = 空清单：不抛错阻断启动，让摄入器走全量重建；签名一并置空确保判变
      entries.clear();
      chunkSignature = null;
    }
  }

  /** 分块参数哈希（如 chunk-size）：null 表示清单缺失/损坏/旧格式，摄入器按全量重建处理。 */
  String chunkSignature() {
    return chunkSignature;
  }

  void chunkSignature(String signature) {
    this.chunkSignature = signature;
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
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("chunkSignature", chunkSignature);
      out.put("entries", entries);
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), out);
    } catch (IOException e) {
      throw new IllegalStateException("知识库摄入清单写入失败: " + file, e);
    }
  }
}
