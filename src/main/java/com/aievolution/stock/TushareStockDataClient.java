package com.aievolution.stock;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * {@link StockDataClient} 的 Tushare pro 真源实现（W17 #1，REST 直连官方 HTTP API： POST {@code
 * api.tushare.pro}，报文 {@code {api_name, token, params, fields}}，响应 {@code data.fields + data.items}
 * 表结构）。
 *
 * <p>映射要点（红线 03 口径显式化）：代码补交易所后缀（6→.SH，0/3→.SZ）；{@code trade_date} yyyyMMdd →
 * LocalDate；财务接口单位为元，领域口径为亿元（{@link FinancialSummary} javadoc）， 换算点收口在本类 {@code yuanToYi}。asOf
 * 取拉取当日——真源没有 mock 的固定快照时点， 拉取时点即数据新鲜度的诚实标注。
 *
 * <p>故障行为（Owner 裁决 2026-10-10）：接口非零码（如 2002 无权限）/ HTTP 故障显式抛 {@link
 * IllegalStateException}——真源链路不静默降级给假数据答案。空凭证构造即抛（fail-fast， 同 rerank 凭证立场）。只在 {@code
 * ai.stock.data-source=tushare} 时装配，mock 留缺省。
 */
@Component
@ConditionalOnProperty(name = "ai.stock.data-source", havingValue = "tushare")
public class TushareStockDataClient implements StockDataClient {

  private static final Logger log = LoggerFactory.getLogger(TushareStockDataClient.class);
  private static final String SOURCE = "tushare";
  private static final DateTimeFormatter TRADE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
  private static final BigDecimal YI = new BigDecimal("100000000");

  private final RestClient restClient;
  private final String token;

  public TushareStockDataClient(
      RestClient.Builder restClientBuilder,
      @Value("${ai.stock.tushare.base-url:https://api.tushare.pro}") String baseUrl,
      @Value("${ai.stock.tushare.token:}") String token,
      @Value("${ai.stock.tushare.timeout-seconds:5}") int timeoutSeconds) {
    if (token == null || token.isBlank()) {
      throw new IllegalArgumentException(
          "ai.stock.data-source=tushare 但凭证为空（ai.stock.tushare.token / env TUSHARE_TOKEN）");
    }
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(timeoutSeconds * 1000);
    factory.setReadTimeout(timeoutSeconds * 1000);
    this.restClient = restClientBuilder.baseUrl(baseUrl).requestFactory(factory).build();
    this.token = token;
  }

  @Override
  public List<DailyQuote> getDailyQuotes(String code, LocalDate from, LocalDate to) {
    TushareResponse response =
        call(
            "daily",
            Map.of(
                "ts_code", toTsCode(code),
                "start_date", from.format(TRADE_DATE),
                "end_date", to.format(TRADE_DATE)),
            "ts_code,trade_date,close");
    return response.rows().stream()
        .map(
            row ->
                new DailyQuote(
                    code,
                    LocalDate.parse(row.get("trade_date"), TRADE_DATE),
                    new BigDecimal(row.get("close")),
                    SOURCE,
                    LocalDate.now()))
        // Tushare 返回新→旧倒序，接口契约要求升序
        .sorted(Comparator.comparing(DailyQuote::date))
        .toList();
  }

  @Override
  public Optional<FinancialSummary> getFinancialSummary(String code, int fiscalYear) {
    TushareResponse response =
        call(
            "income",
            Map.of("ts_code", toTsCode(code), "period", fiscalYear + "1231"),
            "ts_code,end_date,total_revenue,n_income_attr_p");
    return response.rows().stream()
        .findFirst()
        .map(
            row ->
                new FinancialSummary(
                    code,
                    fiscalYear,
                    yuanToYi(row.get("total_revenue")),
                    yuanToYi(row.get("n_income_attr_p")),
                    SOURCE,
                    LocalDate.now()));
  }

  /** 单次调用：组装报文 → POST → 非零码显式报错 → fields+items 表转行映射。 */
  private TushareResponse call(String apiName, Map<String, String> params, String fields) {
    TushareResponse response;
    try {
      response =
          restClient
              .post()
              .uri("/")
              .contentType(MediaType.APPLICATION_JSON)
              .body(Map.of("api_name", apiName, "token", token, "params", params, "fields", fields))
              .retrieve()
              .body(TushareResponse.class);
    } catch (Exception e) {
      throw new IllegalStateException("Tushare 调用失败（" + apiName + "）: " + e.getMessage(), e);
    }
    if (response == null) {
      throw new IllegalStateException("Tushare 响应为空（" + apiName + "）");
    }
    if (response.code() != 0) {
      throw new IllegalStateException(
          "Tushare 接口报错（" + apiName + "）: code=" + response.code() + " msg=" + response.msg());
    }
    log.info("source=tushare api={} rows={}", apiName, response.rows().size());
    return response;
  }

  /** 6 位代码 → 带交易所后缀：6 开头沪市 .SH，其余（0/3 开头）深市 .SZ。 */
  private static String toTsCode(String code) {
    return code.startsWith("6") ? code + ".SH" : code + ".SZ";
  }

  /** 元 → 亿元（两位小数，四舍五入）：Tushare 财务接口单位为元，领域口径为亿元。 */
  private static BigDecimal yuanToYi(String yuan) {
    return new BigDecimal(yuan).divide(YI, 2, RoundingMode.HALF_UP).stripTrailingZeros();
  }

  /** 响应表结构：fields 列名 + items 行值，转 List<Map> 供映射层按列名取值。 */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record TushareResponse(int code, String msg, TushareData data) {
    List<Map<String, String>> rows() {
      if (data == null || data.fields() == null || data.items() == null) {
        return List.of();
      }
      return data.items().stream()
          .map(
              item -> {
                Map<String, String> row = new java.util.HashMap<>();
                for (int i = 0; i < data.fields().size() && i < item.size(); i++) {
                  row.put(data.fields().get(i), item.get(i));
                }
                return row;
              })
          .toList();
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record TushareData(List<String> fields, List<List<String>> items) {}
}
