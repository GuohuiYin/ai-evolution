package com.aievolution.infra;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 日志摘要策略单测（A11 两次即收口首例）：全项目日志截断语义的单一事实源。 */
class LogSummariesTest {

  @Test
  void normalizesWhitespace() {
    assertThat(LogSummaries.summarize("line1\nline2\t line3")).isEqualTo("line1 line2 line3");
  }

  @Test
  void truncatesBeyondMaxWithEllipsis() {
    String longText = "x".repeat(150);
    String summary = LogSummaries.summarize(longText);
    assertThat(summary).hasSize(LogSummaries.MAX_LENGTH + 1).endsWith("…");
  }

  @Test
  void keepsShortTextAsIs() {
    assertThat(LogSummaries.summarize("1700.00")).isEqualTo("1700.00");
  }

  @Test
  void nullBecomesEmpty() {
    assertThat(LogSummaries.summarize(null)).isEmpty();
  }
}
