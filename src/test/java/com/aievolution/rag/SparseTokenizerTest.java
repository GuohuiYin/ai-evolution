package com.aievolution.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * sparse 分词器单测：BM25 编码与覆盖率裁决共用的唯一分词规则（A11-1）。 规则锚点：ASCII 标识符整词（小写化）；CJK 连续段切二元组（单字保留）；重复出现保留（TF
 * 计数原料）。
 */
class SparseTokenizerTest {

  @Test
  void runsSplitOnWhitespaceAndPunctuation() {
    assertThat(SparseTokenizer.runs("12987 工艺的具体含义，SNE Research口径"))
        .containsExactly("12987", "工艺的具体含义", "SNE", "Research口径");
  }

  @Test
  void bm25TermsKeepIdentifierWholeAndLowercased() {
    List<String> terms = SparseTokenizer.bm25Terms("遵循 12987 流程，SNE Research 口径 37%");

    // % 属标点被切分规则丢弃（与覆盖率裁决同规则，W13 起口径），数字整串成 token
    assertThat(terms).contains("12987", "sne", "research", "37");
  }

  @Test
  void bm25TermsSliceCjkRunIntoBigrams() {
    // 「品牌壁垒」四字 → 三个二元组——品牌壁垒案的锚定词必须可命中
    assertThat(SparseTokenizer.bm25Terms("品牌壁垒深厚")).containsExactly("品牌", "牌壁", "壁垒", "垒深", "深厚");
  }

  @Test
  void bm25TermsKeepSingleCjkChar() {
    assertThat(SparseTokenizer.bm25Terms("酒")).containsExactly("酒");
  }

  @Test
  void bm25TermsPreserveDuplicatesForTermFrequency() {
    // TF 是 BM25 权重原料：去重会丢掉词频信号
    assertThat(SparseTokenizer.bm25Terms("茅台茅台")).containsExactly("茅台", "台茅", "茅台");
  }

  @Test
  void blankTextYieldsNoTerms() {
    assertThat(SparseTokenizer.bm25Terms("  ")).isEmpty();
    assertThat(SparseTokenizer.bm25Terms(null)).isEmpty();
  }
}
