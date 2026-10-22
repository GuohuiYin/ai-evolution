package com.aievolution.eval;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * 语料时点清单（W21 #2a）：扫描 knowledge 语料的 {@code .meta.json}（docType/asOf）， 生成 judge 输入侧上下文——"在库证据范围"。消解
 * judge 时效先验误判（W20 揭盲病灶①：judge 用训练时点的 世界先验判"2026 半年报不可能存在"，而语料已轮换入库，changelog 在案）。
 *
 * <p>反腐约定：清单从 meta.json 机械生成，语料轮换（增删文件）即自动跟随， 无手工台账腐烂面；扫描失败或空清单 fail-fast（eval 语境无静默兜底）。
 */
public final class CorpusTimeline {

  private static final String META_PATTERN = "classpath*:knowledge/**/*.meta.json";

  private CorpusTimeline() {}

  /** 生成 Markdown 清单（按文件名排序，稳定输出便于 prompt diff）。 */
  public static String load() {
    List<String> lines = new ArrayList<>();
    try {
      Resource[] metas = new PathMatchingResourcePatternResolver().getResources(META_PATTERN);
      for (Resource meta : metas) {
        String fileName = meta.getFilename();
        if (fileName == null) {
          continue;
        }
        String asOf = extractAsOf(meta.getContentAsString(StandardCharsets.UTF_8));
        // 报告类去掉 .pdf.meta.json 双后缀，笔记类去掉 .md.meta.json
        String doc = fileName.replaceFirst("\\.(pdf|md)\\.meta\\.json$", "");
        lines.add("- " + doc + "（数据时点 " + asOf + "）");
      }
    } catch (IOException e) {
      throw new IllegalStateException("语料时点清单扫描失败（" + META_PATTERN + "）", e);
    }
    if (lines.isEmpty()) {
      throw new IllegalStateException("语料时点清单为空（" + META_PATTERN + "），语料挂载事故");
    }
    lines.sort(Comparator.naturalOrder());
    return String.join("\n", lines);
  }

  /** meta.json 是单行小 JSON（手写维护），取 asOf 字段不引 Jackson——依赖极简，格式崩坏时 fail-fast。 */
  static String extractAsOf(String metaJson) {
    java.util.regex.Matcher m =
        java.util.regex.Pattern.compile("\"asOf\"\\s*:\\s*\"([^\"]+)\"").matcher(metaJson);
    if (!m.find()) {
      throw new IllegalStateException("meta.json 缺 asOf 字段：" + metaJson);
    }
    return m.group(1);
  }
}
