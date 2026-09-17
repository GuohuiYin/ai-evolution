package com.aievolution.rag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 全文规整化：数字/字母与中文粘连处插空格，两侧（摄入/查询）规则必须完全一致。 */
class FullTextNormalizerTest {

  @Test
  void separatesDigitRunGluedToCjk() {
    assertThat(FullTextNormalizer.normalize("遵循12987流程")).isEqualTo("遵循 12987 流程");
  }

  @Test
  void separatesAsciiLettersGluedToCjk() {
    assertThat(FullTextNormalizer.normalize("SNE Research口径37%市占"))
        .isEqualTo("SNE Research 口径 37% 市占");
  }

  @Test
  void pureCjkUntouched() {
    assertThat(FullTextNormalizer.normalize("酿造工艺一年生产周期")).isEqualTo("酿造工艺一年生产周期");
  }

  @Test
  void alreadySpacedUntouched() {
    assertThat(FullTextNormalizer.normalize("遵循 12987 流程")).isEqualTo("遵循 12987 流程");
  }

  @Test
  void nullAndEmptyPassThrough() {
    assertThat(FullTextNormalizer.normalize(null)).isNull();
    assertThat(FullTextNormalizer.normalize("")).isEmpty();
  }
}
