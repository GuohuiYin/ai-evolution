package com.aievolution.stock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Mock 数据源：内置种子数据，供链路开发与测试使用。
 *
 * <p>数据为公开年报口径的示例值，{@code source} 诚实标注为 "mock"——红线 03 对 mock 同样生效： 宁可标注"这是假数据"，也不让假数据伪装成真数据。换真源时设
 * {@code ai.stock.data-source=tushare}。
 */
@Component
@ConditionalOnProperty(name = "ai.stock.data-source", havingValue = "mock", matchIfMissing = true)
public class MockStockDataClient implements StockDataClient {

  private static final String MOCK_SOURCE = "mock";
  private static final LocalDate AS_OF = LocalDate.of(2025, 4, 30);

  /** 种子行情（收盘价，元）：600519 贵州茅台 / 300750 宁德时代，2024 年末若干交易日。 */
  private static final Map<String, List<DailyQuote>> QUOTES =
      Map.of(
          "600519",
              List.of(
                  new DailyQuote(
                      "600519", LocalDate.of(2024, 12, 30), bd("1558.00"), MOCK_SOURCE, AS_OF),
                  new DailyQuote(
                      "600519", LocalDate.of(2024, 12, 31), bd("1525.00"), MOCK_SOURCE, AS_OF)),
          "300750",
              List.of(
                  new DailyQuote(
                      "300750", LocalDate.of(2024, 12, 30), bd("266.50"), MOCK_SOURCE, AS_OF),
                  new DailyQuote(
                      "300750", LocalDate.of(2024, 12, 31), bd("266.00"), MOCK_SOURCE, AS_OF)));

  /** 种子财务摘要（亿元）：2024 年报口径示例值。 */
  private static final Map<String, FinancialSummary> SUMMARIES =
      Map.of(
          "600519:2024",
              new FinancialSummary("600519", 2024, bd("1741.44"), bd("862.28"), MOCK_SOURCE, AS_OF),
          "300750:2024",
              new FinancialSummary(
                  "300750", 2024, bd("3620.13"), bd("507.45"), MOCK_SOURCE, AS_OF));

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }

  @Override
  public List<DailyQuote> getDailyQuotes(String code, LocalDate from, LocalDate to) {
    return QUOTES.getOrDefault(code, List.of()).stream()
        .filter(q -> !q.date().isBefore(from) && !q.date().isAfter(to))
        .sorted((a, b) -> a.date().compareTo(b.date()))
        .toList();
  }

  @Override
  public Optional<FinancialSummary> getFinancialSummary(String code, int fiscalYear) {
    return Optional.ofNullable(SUMMARIES.get(code + ":" + fiscalYear));
  }
}
