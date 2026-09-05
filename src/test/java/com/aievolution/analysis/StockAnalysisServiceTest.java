package com.aievolution.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aievolution.prompt.InvalidPromptVersionException;
import com.aievolution.prompt.PromptLibrary;
import com.aievolution.stock.MockStockDataClient;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;

class StockAnalysisServiceTest {

  private ChatClient chatClient;
  private StockAnalysisService service;

  @BeforeEach
  void setUp() {
    chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
    ChatClient.Builder builder = mock(ChatClient.Builder.class);
    when(builder.build()).thenReturn(chatClient);
    service =
        new StockAnalysisService(builder, new MockStockDataClient(), new PromptLibrary(), 2024);
  }

  @Test
  void rejectsUnknownPromptVersionAsClientError() {
    // 对外参数白名单校验：不存在的 prompt 版本是 400 客户端错误，不是 500 服务端故障
    assertThatThrownBy(() -> service.analyze("600519", "v9"))
        .isInstanceOf(InvalidPromptVersionException.class);
  }

  @Test
  void returnsStructuredReportFromModel() {
    StockAnalysisReport expected =
        new StockAnalysisReport(
            "600519", "2024年营收净利双增", List.of("营收1741.44亿元"), List.of("数据仅一年"), List.of("mock"));
    when(chatClient.prompt(any(Prompt.class)).call().entity(StockAnalysisReport.class))
        .thenReturn(expected);

    StockAnalysisReport report = service.analyze("600519", "v1");

    assertThat(report).isEqualTo(expected);
  }

  @Test
  void injectsTraceableDataIntoPrompt() {
    service.analyze("600519", "v1");

    ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
    org.mockito.Mockito.verify(chatClient).prompt(captor.capture());
    String rendered = captor.getValue().getContents();
    // 红线 03：拼进 prompt 的数据必须带来源与时点，模型才有溯源的原料
    assertThat(rendered).contains("1741.44").contains("mock").contains("2025-04-30");
  }

  @Test
  void rejectsUnknownCodeWithoutCallingModel() {
    assertThatThrownBy(() -> service.analyze("999999", "v1"))
        .isInstanceOf(StockDataNotFoundException.class);
  }

  @Test
  void selectsPromptByVersion() {
    service.analyze("600519", "v0");

    ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
    org.mockito.Mockito.verify(chatClient).prompt(captor.capture());
    // v0 是 zero-shot 裸指令基线：不含 CO-STAR 结构，用于 Step3 改前/改后对比
    assertThat(captor.getValue().getContents()).doesNotContain("CO-STAR");
  }
}
