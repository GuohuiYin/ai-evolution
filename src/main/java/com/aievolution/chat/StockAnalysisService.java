package com.aievolution.chat;

import com.aievolution.stock.DailyQuote;
import com.aievolution.stock.FinancialSummary;
import com.aievolution.stock.StockDataClient;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

/**
 * P0 个股历史表现分析：拉取数据源客户端的结构化数据，拼进版本化 prompt， 由模型输出结构化报告（{@link StockAnalysisReport}）。
 *
 * <p>数据通路二（实时拉取、不进向量库）的首个落地场景——见 docs/plans/w4 的通路辨析。
 */
@Service
public class StockAnalysisService {

  private static final String PROMPT_PREFIX = "stock-analysis-";
  private static final int DEFAULT_FISCAL_YEAR = 2024;

  private final ChatClient chatClient;
  private final StockDataClient stockDataClient;
  private final PromptLibrary promptLibrary;

  public StockAnalysisService(
      ChatClient.Builder chatClientBuilder,
      StockDataClient stockDataClient,
      PromptLibrary promptLibrary) {
    this.chatClient = chatClientBuilder.build();
    this.stockDataClient = stockDataClient;
    this.promptLibrary = promptLibrary;
  }

  public StockAnalysisReport analyze(String code, String promptVersion) {
    FinancialSummary summary =
        stockDataClient
            .getFinancialSummary(code, DEFAULT_FISCAL_YEAR)
            .orElseThrow(() -> new StockDataNotFoundException(code));
    List<DailyQuote> quotes =
        stockDataClient.getDailyQuotes(code, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));

    var prompt =
        new PromptTemplate(promptLibrary.get(PROMPT_PREFIX + promptVersion))
            .create(
                Map.of(
                    "code", code,
                    "financialData", render(summary),
                    "quoteData", render(quotes)));
    return chatClient.prompt(prompt).call().entity(StockAnalysisReport.class);
  }

  // 红线 03：拼进 prompt 的每条数据自带来源与时点，模型才有"可溯源"的原料
  private String render(FinancialSummary s) {
    return "%d 年营收 %s 亿元、净利 %s 亿元（来源: %s，时点: %s）"
        .formatted(s.fiscalYear(), s.revenue(), s.netProfit(), s.source(), s.asOf());
  }

  private String render(List<DailyQuote> quotes) {
    if (quotes.isEmpty()) {
      return "（数据源暂无行情记录）";
    }
    return quotes.stream()
        .map(q -> "%s 收盘价 %s 元（来源: %s，时点: %s）".formatted(q.date(), q.close(), q.source(), q.asOf()))
        .collect(Collectors.joining("\n"));
  }
}
