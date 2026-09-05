package com.aievolution.stock;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DailyQuoteTest {

  @Test
  void promptTextCarriesCodeSourceAndAsOf() {
    DailyQuote quote =
        new DailyQuote(
            "600519",
            LocalDate.of(2024, 12, 31),
            new BigDecimal("1525.00"),
            "mock",
            LocalDate.of(2025, 4, 30));

    assertThat(quote.toPromptText())
        .isEqualTo("600519 2024-12-31 收盘价 1525.00 元（来源: mock，时点: 2025-04-30）");
  }
}
