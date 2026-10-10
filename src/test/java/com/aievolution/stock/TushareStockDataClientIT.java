package com.aievolution.stock;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * W17 #2 Tushare 真源冒烟：真实 API 调通 600519 行情 + 财务各一条， 数值对公开年报口径区间断言（非精确值——防数据源修订抖动）。 {@code
 * TUSHARE_TOKEN} 缺失自动 skip：CI 无 token 不拖构建，本地 token 到位即跑。
 */
class TushareStockDataClientIT {

  @Test
  void realTushareReturnsMaotaiQuotesAndFinancials() {
    String token = System.getenv("TUSHARE_TOKEN");
    Assumptions.assumeTrue(token != null && !token.isBlank(), "TUSHARE_TOKEN 未配置，跳过真源冒烟");

    TushareStockDataClient client =
        new TushareStockDataClient(RestClient.builder(), "https://api.tushare.pro", token, 10);

    List<DailyQuote> quotes =
        client.getDailyQuotes("600519", LocalDate.of(2024, 12, 30), LocalDate.of(2024, 12, 31));
    assertThat(quotes).isNotEmpty();
    assertThat(quotes).allSatisfy(q -> assertThat(q.source()).isEqualTo("tushare"));
    // 茅台 2024 年末收盘价公开口径 1500+ 元区间
    assertThat(quotes.getLast().close()).isGreaterThan(new BigDecimal("1000"));

    Optional<FinancialSummary> summary = client.getFinancialSummary("600519", 2024);
    assertThat(summary).isPresent();
    // 2024 年报公开口径：营收 1741.44 亿、归母净利 862.28 亿（区间防修订抖动）
    assertThat(summary.get().revenue()).isBetween(new BigDecimal("1700"), new BigDecimal("1800"));
    assertThat(summary.get().netProfit()).isBetween(new BigDecimal("800"), new BigDecimal("900"));
  }
}
