package com.aievolution.stock;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 股票数据源客户端（接口即契约）。
 *
 * <p>W4 mock 先行：{@code MockStockDataClient} 为首实现；W5 换 Tushare/东财真源时新增实现类， 调用方依赖本接口、零改动（开闭原则）。
 */
public interface StockDataClient {

  /** 查询区间日行情，按日期升序；未知代码返回空列表而非抛异常（调用方多为 LLM 工具层， 空结果是比异常更友好的降级信号）。 */
  List<DailyQuote> getDailyQuotes(String code, LocalDate from, LocalDate to);

  /** 查询年度财务摘要；未覆盖的代码/年份返回 {@link Optional#empty()}。 */
  Optional<FinancialSummary> getFinancialSummary(String code, int fiscalYear);
}
