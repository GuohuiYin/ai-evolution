package com.aievolution.rag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.ai.document.Document;

/**
 * W13 #2b sparse 路应用侧覆盖率排序：Qdrant MatchText 只做候选网（宽捞）， 命中与否的最终裁决在应用侧按"查询 token 在文档规整化文本中的子串覆盖率"完成。
 *
 * <p>为什么需要这层（2026-09-17 probe 实证链）：
 *
 * <ul>
 *   <li>MatchText 是严格 AND——自然语言问句只要有一个词不在文中即零命中，无法服务真实问句
 *   <li>multilingual 分词器把紧贴中文的数字/字母粘连成单 token（"12987流程"不可分）， 且纯数字单 token
 *       查询被直接丢弃——代号类查询必须靠摄入/查询双侧规整化（{@link FullTextNormalizer}）+ 应用侧子串裁决兜底
 * </ul>
 *
 * <p>通过规则（精度优先，adversarial 负例是硬约束）：
 *
 * <ul>
 *   <li>查询含 ASCII 标识符 token（如 12987 / SNE / 37%）：<strong>全部</strong>子串命中才放行 （代号是硬证据；假代号查询一律拒）
 *   <li>纯中文查询：整 token 子串命中即放行；否则按 CJK 二元组覆盖率 ≥ 0.5 放行 （"比亚迪刀片电池的技术参数"对 catl.md 覆盖率 1/10，被拒——W8
 *       adversarial 判例）
 * </ul>
 *
 * <p>排名：标识符命中数优先，其后整 token 命中数与二元组覆盖率；同分按文档 ID 字典序，输出可复现。 这是 BM25 的轻量近似（无 IDF、子串即词频），诚实定位见 ADR-0017
 * sparse 候选 B。
 */
public final class SparseCoverage {

  /** CJK 二元组覆盖率放行阈值（精度优先：宁缺毋滥，召回缺口由 dense 路与 rerank 兜）。 */
  static final double BIGRAM_THRESHOLD = 0.5;

  private SparseCoverage() {}

  /** 应用侧裁决 + 排序：返回通过裁决的候选，按覆盖率降序、同分按 ID 字典序。 */
  public static List<Document> rank(String normalizedQuery, List<Document> candidates, int limit) {
    record Scored(Document doc, double score) {}
    List<Scored> passed = new ArrayList<>();
    for (Document candidate : candidates) {
      double score = score(normalizedQuery, candidate.getText());
      if (score >= 0) {
        passed.add(new Scored(candidate, score));
      }
    }
    passed.sort(
        java.util.Comparator.comparingDouble(Scored::score)
            .reversed()
            .thenComparing(s -> s.doc().getId()));
    return passed.stream().limit(limit).map(Scored::doc).toList();
  }

  /** 覆盖率打分：负分 = 拒。 */
  static double score(String normalizedQuery, String docText) {
    if (docText == null) {
      return -1;
    }
    String doc = docText.toLowerCase(Locale.ROOT);
    List<String> tokens = queryTokens(normalizedQuery);
    List<String> identifiers = tokens.stream().filter(SparseCoverage::isIdentifier).toList();
    List<String> cjk = tokens.stream().filter(t -> !isIdentifier(t)).toList();

    long identifierHits =
        identifiers.stream().filter(t -> doc.contains(t.toLowerCase(Locale.ROOT))).count();
    if (!identifiers.isEmpty() && identifierHits < identifiers.size()) {
      return -1; // 代号是硬证据：假代号查询一律拒
    }

    long wholeHits = cjk.stream().filter(doc::contains).count();
    List<String> bigrams = cjkBigrams(cjk);
    long bigramHits = bigrams.stream().filter(doc::contains).count();
    double bigramCoverage = bigrams.isEmpty() ? 0 : (double) bigramHits / bigrams.size();

    if (!identifiers.isEmpty()) {
      return identifierHits * 10 + wholeHits * 2 + bigramCoverage; // 代号全中即放行
    }
    if (wholeHits > 0) {
      return wholeHits * 2 + bigramCoverage; // 整 token 命中即放行
    }
    return bigramCoverage >= BIGRAM_THRESHOLD ? bigramCoverage : -1;
  }

  /** 查询 token 化：规整化文本按空白与标点切分。 */
  static List<String> queryTokens(String normalizedQuery) {
    if (normalizedQuery == null || normalizedQuery.isBlank()) {
      return List.of();
    }
    Set<String> tokens = new LinkedHashSet<>();
    for (String piece : normalizedQuery.split("[\\s\\p{P}]+")) {
      if (!piece.isEmpty()) {
        tokens.add(piece);
      }
    }
    return List.copyOf(tokens);
  }

  /** ASCII 标识符 token（代号/编号/年份/英文词）：是否含字母或数字。 */
  static boolean isIdentifier(String token) {
    return token
        .chars()
        .anyMatch(
            ch -> (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z'));
  }

  /** CJK token 的连续二元组（跨 token 不切）。 */
  static List<String> cjkBigrams(List<String> cjkTokens) {
    List<String> bigrams = new ArrayList<>();
    for (String token : cjkTokens) {
      for (int i = 0; i + 2 <= token.length(); i++) {
        bigrams.add(token.substring(i, i + 2));
      }
    }
    return bigrams;
  }
}
