package com.aievolution.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.aievolution.stock.MockStockDataClient;
import org.junit.jupiter.api.Test;

class StockDataToolsTest {

  private final StockDataTools tools = new StockDataTools(new MockStockDataClient());

  @Test
  void quotesOutputCarriesPriceAndTraceableMetadata() {
    String output = tools.getDailyQuotes("600519", "2024-12-01", "2024-12-31");

    // 红线 03 落到工具层：模型拿到的每条数据自带来源与时点，才有"可溯源"的原料
    assertThat(output).contains("1525.00").contains("mock").contains("2025-04-30");
  }

  @Test
  void financialSummaryOutputCarriesTraceableMetadata() {
    String output = tools.getFinancialSummary("600519", 2024);

    assertThat(output).contains("1741.44").contains("862.28").contains("mock");
  }

  @Test
  void unknownCodeReturnsGracefulTextInsteadOfThrowing() {
    // 工具的调用方是模型：空结果是比异常更友好的降级信号（与 StockDataClient 同一哲学）
    String output = tools.getDailyQuotes("999999", "2024-01-01", "2024-12-31");

    assertThat(output).contains("未覆盖").doesNotContain("Exception");
  }

  @Test
  void unseededFiscalYearReturnsGracefulText() {
    assertThat(tools.getFinancialSummary("600519", 1999)).contains("未覆盖");
  }
}
