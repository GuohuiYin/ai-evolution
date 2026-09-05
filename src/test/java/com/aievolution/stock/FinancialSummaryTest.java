package com.aievolution.stock;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FinancialSummaryTest {

  @Test
  void promptTextCarriesCodeSourceAndAsOf() {
    FinancialSummary summary =
        new FinancialSummary(
            "600519",
            2024,
            new BigDecimal("1741.44"),
            new BigDecimal("862.28"),
            "mock",
            LocalDate.of(2025, 4, 30));

    // 红线 03：渲染进 prompt / 工具输出的文本必须带来源与时点，格式全项目唯一（A12）
    assertThat(summary.toPromptText())
        .isEqualTo("600519 2024 年营收 1741.44 亿元、净利 862.28 亿元（来源: mock，时点: 2025-04-30）");
  }
}
