package com.aievolution.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * sparse 分词器（A11-1 单点收口）：BM25 编码（W16 #2 A3-lite）与覆盖率裁决（W13 #2b） 共用的唯一分词规则。
 *
 * <p>规则：按空白与标点切 run；ASCII 标识符（代号/编号/英文词）整词保留并小写化； CJK 连续段切二元组（单字保留）。输入文本默认已经 {@link
 * FullTextNormalizer} 规整（摄入侧行为，ASCII 与 CJK 粘连已被空格分开）。
 */
final class SparseTokenizer {

  private SparseTokenizer() {}

  /** 切 run：空白/标点为界，保序保重复（TF 计数原料）。 */
  static List<String> runs(String text) {
    List<String> runs = new ArrayList<>();
    if (text == null || text.isBlank()) {
      return runs;
    }
    for (String piece : text.split("[\\s\\p{P}]+")) {
      if (!piece.isEmpty()) {
        runs.add(piece);
      }
    }
    return runs;
  }

  /**
   * BM25 词项流：标识符整词（小写）+ CJK 二元组（单字 run 原样保留）——重复保留 （去重由编码器按权重需要处理）。与覆盖率裁决的二元组口径同源，{@code 品牌壁垒}
   * 类锚定词可被字面命中（W16 #2 靶案）。
   */
  static List<String> bm25Terms(String text) {
    List<String> terms = new ArrayList<>();
    for (String run : runs(text)) {
      if (isIdentifier(run)) {
        terms.add(run.toLowerCase(Locale.ROOT));
      } else if (run.length() < 2) {
        terms.add(run);
      } else {
        terms.addAll(cjkBigrams(run));
      }
    }
    return terms;
  }

  /** ASCII 标识符 run（代号/编号/年份/英文词）：是否含字母或数字。 */
  static boolean isIdentifier(String run) {
    return run.chars()
        .anyMatch(
            ch -> (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z'));
  }

  /** CJK run 的连续二元组（不足两字的 run 不出二元组——与覆盖率裁决口径一致）。 */
  static List<String> cjkBigrams(String cjkRun) {
    List<String> bigrams = new ArrayList<>();
    for (int i = 0; i + 2 <= cjkRun.length(); i++) {
      bigrams.add(cjkRun.substring(i, i + 2));
    }
    return bigrams;
  }
}
