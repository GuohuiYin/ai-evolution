package com.aievolution.stock;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MockStockDataClientTest {

  private final MockStockDataClient client = new MockStockDataClient();

  @Test
  void returnsDailyQuotesInRangeWithTraceableMetadata() {
    List<DailyQuote> quotes =
        client.getDailyQuotes("600519", LocalDate.of(2024, 12, 1), LocalDate.of(2024, 12, 31));

    assertThat(quotes).isNotEmpty();
    assertThat(quotes).allSatisfy(q -> assertThat(q.code()).isEqualTo("600519"));
    // 红线 03：每条数据必须带数据源与时点，mock 也不例外
    assertThat(quotes)
        .allSatisfy(
            q -> {
              assertThat(q.source()).isNotBlank();
              assertThat(q.asOf()).isNotNull();
            });
    // 时间升序，调用方无需再排序
    assertThat(quotes).isSortedAccordingTo((a, b) -> a.date().compareTo(b.date()));
  }

  @Test
  void filtersQuotesOutsideRequestedRange() {
    List<DailyQuote> quotes =
        client.getDailyQuotes("600519", LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 30));

    assertThat(quotes).isEmpty();
  }

  @Test
  void returnsEmptyForUnknownCodeInsteadOfThrowing() {
    assertThat(
            client.getDailyQuotes("999999", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)))
        .isEmpty();
    assertThat(client.getFinancialSummary("999999", 2024)).isEmpty();
  }

  @Test
  void returnsFinancialSummaryWithTraceableMetadata() {
    Optional<FinancialSummary> summary = client.getFinancialSummary("600519", 2024);

    assertThat(summary).isPresent();
    FinancialSummary s = summary.get();
    assertThat(s.fiscalYear()).isEqualTo(2024);
    assertThat(s.revenue()).isGreaterThan(BigDecimal.ZERO);
    assertThat(s.netProfit()).isGreaterThan(BigDecimal.ZERO);
    assertThat(s.source()).isNotBlank();
    assertThat(s.asOf()).isNotNull();
  }

  @Test
  void returnsEmptySummaryForUnseededFiscalYear() {
    assertThat(client.getFinancialSummary("600519", 1999)).isEmpty();
  }
}
