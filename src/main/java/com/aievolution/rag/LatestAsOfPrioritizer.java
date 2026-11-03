package com.aievolution.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 最新口径优先重排（W26 #5，「最新口径优先」缺陷族修复路线 A）。
 *
 * <p>病灶：无年份查询（「茅台的总股本是多少股」）语义检索会把中间/旧披露口径排在前面，模型照单 全收即时点错位（badcase 694fcbe6）。此处对最终命中做稳定重排：带 {@code
 * asOf}（ISO 日期，词 典序即可比）的片段按数据时点降序提前，无 {@code asOf} 的片段保持原相对顺序殿后——检索相关性 仍决定「进不进名单」，时点只决定「同榜谁先被看见」。
 *
 * <p>时点限定题（query 含四位年份，如「2025 年年报披露的…」）不动排序——精确导航考点（「2025 年报口径 1,252,270,215 股」金题）依赖语义排序原样生效。
 */
@Component
public class LatestAsOfPrioritizer {

  private static final Pattern YEAR_PINNED = Pattern.compile(".*(19|20)\\d{2}.*");

  private final boolean enabled;

  public LatestAsOfPrioritizer(
      @Value("${ai.rag.latest-asof-priority.enabled:true}") boolean enabled) {
    this.enabled = enabled;
  }

  /**
   * 无年份查询：按时点降序稳定重排；时点限定题或开关关闭：原样返回。
   *
   * @return 重排后的新列表；未改动语义时返回原列表
   */
  public List<Document> prioritize(String query, List<Document> hits) {
    if (!enabled || hits.size() < 2 || YEAR_PINNED.matcher(query).matches()) {
      return hits;
    }
    List<Document> dated = new ArrayList<>();
    List<Document> undated = new ArrayList<>();
    for (Document hit : hits) {
      (asOf(hit) != null ? dated : undated).add(hit);
    }
    if (dated.isEmpty()) {
      return hits;
    }
    // 稳定排序：同 asOf 保持召回相对顺序；ISO 日期词典序即时间序
    dated.sort(Comparator.comparing(LatestAsOfPrioritizer::asOf).reversed());
    List<Document> reordered = new ArrayList<>(hits.size());
    reordered.addAll(dated);
    reordered.addAll(undated);
    return reordered.equals(hits) ? hits : reordered;
  }

  /** 片段数据时点（meta.json 三件套之一）；缺失或非法格式按无时点处理。 */
  private static String asOf(Document doc) {
    Object value = doc.getMetadata().get("asOf");
    if (value == null) {
      return null;
    }
    String text = String.valueOf(value);
    return text.matches("\\d{4}-\\d{2}-\\d{2}") ? text : null;
  }
}
