package com.aievolution.tool;

import com.aievolution.stock.DailyQuote;
import com.aievolution.stock.StockDataClient;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 行情/财务工具：把 {@link StockDataClient} 包装为模型可自主调用的工具（W5 Function Calling）。
 *
 * <p>设计要点：工具描述与参数说明是"模型的使用手册"——描述质量直接决定 Agent 智商； 输出必须自带来源与时点（红线 03 落到工具层），模型才有可溯源的原料。
 */
@Component
public class StockDataTools {

  private final StockDataClient stockDataClient;

  public StockDataTools(StockDataClient stockDataClient) {
    this.stockDataClient = stockDataClient;
  }

  @Tool(description = "查询指定股票的区间日行情（收盘价，单位：元）。当用户问到股价、行情、走势时使用。" + "返回数据含数据源与时点，回答中必须注明。")
  public String getDailyQuotes(
      @ToolParam(description = "股票代码，如 600519") String code,
      @ToolParam(description = "开始日期，ISO 格式，如 2024-01-01") String from,
      @ToolParam(description = "结束日期，ISO 格式，如 2024-12-31") String to) {
    List<DailyQuote> quotes =
        stockDataClient.getDailyQuotes(code, LocalDate.parse(from), LocalDate.parse(to));
    if (quotes.isEmpty()) {
      return "数据源未覆盖 %s 在该区间的行情数据".formatted(code);
    }
    return quotes.stream().map(DailyQuote::toPromptText).collect(Collectors.joining("\n"));
  }

  @Tool(description = "查询指定股票的年度财务摘要（营收/净利润，单位：亿元）。当用户问到营收、利润、财务表现时使用。" + "返回数据含数据源与时点，回答中必须注明。")
  public String getFinancialSummary(
      @ToolParam(description = "股票代码，如 600519") String code,
      @ToolParam(description = "会计年度，如 2024") int fiscalYear) {
    return stockDataClient
        .getFinancialSummary(code, fiscalYear)
        .map(com.aievolution.stock.FinancialSummary::toPromptText)
        .orElse("数据源未覆盖 %s %d 年的财务数据".formatted(code, fiscalYear));
  }
}
