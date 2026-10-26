package com.aievolution.rag;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * 主体提取器（W23 #2a）：规则先行——从查询文本识别提到的在库主体（规范名）。 命中途径三选一：规范名 / 别名（大小写不敏感）/ 6 位股票代码。
 *
 * <p>输出为 {@link LinkedHashSet}，词典顺序即检测顺序——同输入输出可复现（A3 判罚留痕可比对）。
 */
public class SubjectExtractor {

  private final List<SubjectEntry> entries;

  public SubjectExtractor(List<SubjectEntry> entries) {
    this.entries = List.copyOf(entries);
  }

  public LinkedHashSet<String> extract(String query) {
    LinkedHashSet<String> found = new LinkedHashSet<>();
    if (query == null || query.isBlank()) {
      return found;
    }
    String lower = query.toLowerCase(Locale.ROOT);
    for (SubjectEntry entry : entries) {
      if (matches(lower, entry)) {
        found.add(entry.name());
      }
    }
    return found;
  }

  private static boolean matches(String lowerQuery, SubjectEntry entry) {
    if (entry.code() != null && !entry.code().isBlank() && lowerQuery.contains(entry.code())) {
      return true;
    }
    if (lowerQuery.contains(entry.name().toLowerCase(Locale.ROOT))) {
      return true;
    }
    return entry.aliases().stream().anyMatch(a -> lowerQuery.contains(a.toLowerCase(Locale.ROOT)));
  }
}
