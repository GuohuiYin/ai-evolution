package com.aievolution.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * W17 #1 Tushare 真源契约：官方 HTTP API（POST api.tushare.pro，{api_name, token, params, fields}）映射到 {@link
 * DailyQuote}/{@link FinancialSummary}—— 代码补交易所后缀（6→SH，0/3→SZ）、trade_date
 * yyyyMMdd→LocalDate、财务元→亿元（接口口径单位为元的显式换算，红线 03）； 接口非零码/HTTP 故障显式报错（Owner 裁决：真源链路不给假数据答案）；空凭证构造
 * fail-fast。
 *
 * <p>测试走 JDK HttpServer 本地桩（真实 HTTP 往返，含超时工厂全链路）——MockRestServiceServer 会被实现侧的
 * requestFactory（超时配置）覆盖拦截器，拦不住。
 */
class TushareStockDataClientTest {

  private HttpServer stub;
  private String baseUrl;
  private final Deque<String> responses = new ArrayDeque<>();
  private final List<String> requestBodies = new ArrayList<>();
  private TushareStockDataClient client;

  @BeforeEach
  void startStub() throws IOException {
    stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    stub.createContext(
        "/",
        exchange -> {
          requestBodies.add(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] body = responses.poll().getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    stub.start();
    baseUrl = "http://127.0.0.1:" + stub.getAddress().getPort();
    client = new TushareStockDataClient(RestClient.builder(), baseUrl, "test-token", 5);
  }

  @AfterEach
  void stopStub() {
    stub.stop(0);
  }

  private static String ok(String fieldsJson, String itemsJson) {
    return "{\"code\":0,\"msg\":\"\",\"data\":{\"fields\":"
        + fieldsJson
        + ",\"items\":"
        + itemsJson
        + "}}";
  }

  @Test
  void dailyQuotesMapFieldsConvertDateAndSortAscending() {
    responses.add(
        ok(
            "[\"ts_code\",\"trade_date\",\"close\"]",
            // Tushare 数字字段不戴引号（真实响应形态），且新→旧倒序
            "[[\"600519.SH\",\"20241231\",1525.00],[\"600519.SH\",\"20241230\",1558.00]]"));

    List<DailyQuote> quotes =
        client.getDailyQuotes("600519", LocalDate.of(2024, 12, 30), LocalDate.of(2024, 12, 31));

    // 契约要求升序
    assertThat(quotes).hasSize(2);
    assertThat(quotes.getFirst().date()).isEqualTo(LocalDate.of(2024, 12, 30));
    assertThat(quotes.getFirst().close()).isEqualByComparingTo(new BigDecimal("1558.00"));
    assertThat(quotes.get(1).date()).isEqualTo(LocalDate.of(2024, 12, 31));
    assertThat(quotes.getFirst().code()).isEqualTo("600519");
    assertThat(quotes.getFirst().source()).isEqualTo("tushare");
    assertThat(quotes.getFirst().asOf()).isEqualTo(LocalDate.now());
    String body = requestBodies.getFirst();
    assertThat(body)
        .contains("\"api_name\":\"daily\"")
        .contains("\"ts_code\":\"600519.SH\"")
        .contains("\"start_date\":\"20241230\"")
        .contains("\"token\":\"test-token\"");
  }

  @Test
  void szCodeMapsToSzSuffix() {
    responses.add(ok("[\"ts_code\"]", "[]"));

    client.getDailyQuotes("300750", LocalDate.of(2024, 12, 30), LocalDate.of(2024, 12, 31));

    assertThat(requestBodies.getFirst()).contains("\"ts_code\":\"300750.SZ\"");
  }

  @Test
  void financialSummaryConvertsYuanToHundredMillion() {
    responses.add(
        ok(
            "[\"ts_code\",\"end_date\",\"total_revenue\",\"n_income_attr_p\"]",
            "[[\"600519.SH\",\"20241231\",174144000000.00,86228000000.00]]"));

    Optional<FinancialSummary> summary = client.getFinancialSummary("600519", 2024);

    assertThat(summary).isPresent();
    // 接口单位为元，领域口径为亿元（FinancialSummary javadoc）——换算点必须显式
    assertThat(summary.get().revenue()).isEqualByComparingTo(new BigDecimal("1741.44"));
    assertThat(summary.get().netProfit()).isEqualByComparingTo(new BigDecimal("862.28"));
    assertThat(summary.get().fiscalYear()).isEqualTo(2024);
    assertThat(summary.get().source()).isEqualTo("tushare");
    assertThat(requestBodies.getFirst())
        .contains("\"api_name\":\"income\"")
        .contains("\"period\":\"20241231\"");
  }

  @Test
  void emptyItemsReturnEmptyResults() {
    responses.add(ok("[\"ts_code\"]", "[]"));
    responses.add(ok("[\"ts_code\"]", "[]"));

    assertThat(client.getDailyQuotes("999999", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2)))
        .isEmpty();
    assertThat(client.getFinancialSummary("999999", 2024)).isEmpty();
  }

  @Test
  void nonZeroApiCodeThrowsExplicitly() {
    responses.add("{\"code\":2002,\"msg\":\"没有权限\",\"data\":null}");

    // Owner 裁决：真源链路故障显式报错，不静默降级给假数据答案
    assertThatThrownBy(
            () ->
                client.getDailyQuotes("600519", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("2002")
        .hasMessageContaining("没有权限");
  }

  @Test
  void blankTokenFailsFastAtConstruction() {
    assertThatThrownBy(() -> new TushareStockDataClient(RestClient.builder(), baseUrl, " ", 5))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("TUSHARE_TOKEN");
  }
}
