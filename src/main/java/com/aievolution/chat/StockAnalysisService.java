package com.aievolution.chat;

import com.aievolution.stock.DailyQuote;
import com.aievolution.stock.StockDataClient;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * P0 个股历史表现分析：拉取数据源客户端的结构化数据，拼进版本化 prompt， 由模型输出结构化报告（{@link StockAnalysisReport}）。
 *
 * <p>数据通路二（实时拉取、不进向量库）的首个落地场景——见 docs/plans/w4 的通路辨析。
 */
@Service
public class StockAnalysisService {

  private static final String PROMPT_PREFIX = "stock-analysis-";

  private final ChatClient chatClient;
  private final StockDataClient stockDataClient;
  private final PromptLibrary promptLibrary;
  // 分析年度是策略值（随时间/需求调整）而非数学常量：配置化，约定 A11-2
  private final int analysisYear;

  public StockAnalysisService(
      ChatClient.Builder chatClientBuilder,
      StockDataClient stockDataClient,
      PromptLibrary promptLibrary,
      @Value("${ai.stock.analysis-year:2024}") int analysisYear) {
    this.chatClient = chatClientBuilder.build();
    this.stockDataClient = stockDataClient;
    this.promptLibrary = promptLibrary;
    this.analysisYear = analysisYear;
  }

  public StockAnalysisReport analyze(String code, String promptVersion) {
    String promptName = PROMPT_PREFIX + promptVersion;
    if (!promptLibrary.exists(promptName)) {
      throw new InvalidPromptVersionException(promptVersion);
    }
    var summary =
        stockDataClient
            .getFinancialSummary(code, analysisYear)
            .orElseThrow(() -> new StockDataNotFoundException(code));
    List<DailyQuote> quotes =
        stockDataClient.getDailyQuotes(
            code, LocalDate.of(analysisYear, 1, 1), LocalDate.of(analysisYear, 12, 31));

    var prompt =
        new PromptTemplate(promptLibrary.get(promptName))
            .create(
                Map.of(
                    "code", code,
                    "financialData", summary.toPromptText(),
                    "quoteData", render(quotes)));
    return chatClient.prompt(prompt).call().entity(StockAnalysisReport.class);
  }

  private String render(List<DailyQuote> quotes) {
    if (quotes.isEmpty()) {
      return "（数据源暂无行情记录）";
    }
    return quotes.stream().map(DailyQuote::toPromptText).collect(Collectors.joining("\n"));
  }
}
