package com.aievolution.loop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aievolution.tool.AnnouncementTools;
import com.aievolution.tool.ExternalToolClient;
import com.aievolution.tool.StockDataTools;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** W11 #3a：工具注册表——按名派发 + JSON 入参解析。不可派发一律降级为错误观察喂回模型，不抛异常击穿循环。 */
class ToolRegistryTest {

  private StockDataTools stockDataTools;
  private AnnouncementTools announcementTools;
  private ToolRegistry registry;

  @BeforeEach
  void setUp() {
    stockDataTools = mock(StockDataTools.class);
    announcementTools = mock(AnnouncementTools.class);
    registry = new ToolRegistry(stockDataTools, announcementTools);
  }

  @Test
  void dispatchesDailyQuotesWithParsedArgs() {
    when(stockDataTools.getDailyQuotes("600519", "2024-01-01", "2024-12-31"))
        .thenReturn("收盘价 1700（来源：Tushare，截至 2024-12-31）");

    String observation =
        registry.execute(
            "getDailyQuotes",
            "{\"code\":\"600519\",\"from\":\"2024-01-01\",\"to\":\"2024-12-31\"}");

    assertThat(observation).contains("1700");
    verify(stockDataTools).getDailyQuotes("600519", "2024-01-01", "2024-12-31");
  }

  @Test
  void dispatchesFinancialSummaryCoercingFiscalYear() {
    when(stockDataTools.getFinancialSummary("600519", 2024)).thenReturn("营收 1741.44 亿元");

    String observation =
        registry.execute("getFinancialSummary", "{\"code\":\"600519\",\"fiscalYear\":2024}");

    assertThat(observation).contains("1741.44");
  }

  @Test
  void dispatchesAnnouncementSearch() {
    when(announcementTools.searchAnnouncements("茅台酿造工艺")).thenReturn("【来源: maotai.md】12987 工艺");

    String observation = registry.execute("searchAnnouncements", "{\"query\":\"茅台酿造工艺\"}");

    assertThat(observation).contains("12987");
  }

  @Test
  void unknownToolBecomesErrorObservation() {
    String observation = registry.execute("hackTool", "{}");

    assertThat(observation).contains("未知工具").contains("hackTool");
    // 可用工具清单随错误观察回喂，模型可自我纠正（拒绝执行，非回落派发）
    assertThat(observation).contains("getDailyQuotes").contains("searchAnnouncements");
  }

  @Test
  void malformedJsonBecomesErrorObservation() {
    String observation = registry.execute("getDailyQuotes", "不是JSON");

    assertThat(observation).contains("入参解析失败");
  }

  @Test
  void missingParamBecomesErrorObservation() {
    String observation = registry.execute("getFinancialSummary", "{\"code\":\"600519\"}");

    assertThat(observation).contains("入参解析失败").contains("fiscalYear");
  }

  @Test
  void exposesToolSpecsForPrompt() {
    // #3b prompt 的"工具手册"来源：名称 + 用途 + 入参 schema 三要素
    assertThat(registry.specs())
        .anySatisfy(
            s -> {
              assertThat(s.name()).isEqualTo("getDailyQuotes");
              assertThat(s.description()).contains("行情");
              assertThat(s.paramsSchema()).contains("code").contains("from").contains("to");
            });
    assertThat(registry.specs()).hasSize(3);
  }

  // ===== W12 #1c：第 4 工具 fetchWebPage（外部 MCP 取数端口挂入工具面板）=====

  @Test
  void dispatchesFetchWebPageToExternalToolClient() {
    ExternalToolClient externalToolClient = mock(ExternalToolClient.class);
    when(externalToolClient.fetchWebPage("https://example.com/notice")).thenReturn("公告正文摘录");
    ToolRegistry withExternal =
        new ToolRegistry(stockDataTools, announcementTools, Optional.of(externalToolClient));

    String observation =
        withExternal.execute("fetchWebPage", "{\"url\":\"https://example.com/notice\"}");

    assertThat(observation).contains("公告正文摘录");
    verify(externalToolClient).fetchWebPage("https://example.com/notice");
  }

  @Test
  void fetchWebPageAbsentWhenNoExternalToolClient() {
    // MCP Client 关闭场景（两参构造）：面板与派发边界同步收窄，fetchWebPage 视为未知工具
    String observation = registry.execute("fetchWebPage", "{\"url\":\"https://example.com\"}");

    assertThat(observation).contains("未知工具");
    assertThat(registry.specs()).noneMatch(s -> s.name().equals("fetchWebPage"));
  }

  @Test
  void externalToolSpecAppearsInManualWhenPresent() {
    ToolRegistry withExternal =
        new ToolRegistry(
            stockDataTools, announcementTools, Optional.of(mock(ExternalToolClient.class)));

    assertThat(withExternal.specs()).hasSize(4);
    assertThat(withExternal.specs())
        .anySatisfy(
            s -> {
              assertThat(s.name()).isEqualTo("fetchWebPage");
              assertThat(s.paramsSchema()).contains("url");
            });
  }

  @Test
  void unknownToolMessageListsAvailableToolsFromSpecs() {
    // 可用工具清单与 specs 同源：挂上外部端口后错误观察自动含第 4 工具
    ToolRegistry withExternal =
        new ToolRegistry(
            stockDataTools, announcementTools, Optional.of(mock(ExternalToolClient.class)));

    assertThat(withExternal.execute("hackTool", "{}")).contains("fetchWebPage");
  }
}
