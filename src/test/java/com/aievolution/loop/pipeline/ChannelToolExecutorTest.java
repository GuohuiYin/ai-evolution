package com.aievolution.loop.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aievolution.loop.ToolExecutor;
import com.aievolution.loop.ToolRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

/** W22 #1c：Builder 通路白名单——子问题限定的通路既收窄工具手册（prompt 层）， 也拦执行层越权（模型跨通路乱调给错误观察喂回，自我纠正）。 */
class ChannelToolExecutorTest {

  private static final List<ToolRegistry.ToolSpec> SPECS =
      List.of(
          new ToolRegistry.ToolSpec("getDailyQuotes", "查行情", "{}"),
          new ToolRegistry.ToolSpec("getFinancialSummary", "查财务摘要", "{}"),
          new ToolRegistry.ToolSpec("searchAnnouncements", "检索语料", "{}"));

  @Test
  void ragChannelAllowsSearchAnnouncementsOnly() {
    assertThat(ChannelToolExecutor.toolsFor(Channel.RAG))
        .containsExactlyInAnyOrder("searchAnnouncements");
    assertThat(ChannelToolExecutor.toolsFor(Channel.ANNOUNCEMENT))
        .containsExactlyInAnyOrder("searchAnnouncements");
    assertThat(ChannelToolExecutor.toolsFor(Channel.STOCK))
        .containsExactlyInAnyOrder("getDailyQuotes", "getFinancialSummary");
    assertThat(ChannelToolExecutor.toolsFor(Channel.NONE)).isEmpty();
  }

  @Test
  void allowedToolDelegates() {
    ToolExecutor delegate = mock(ToolExecutor.class);
    when(delegate.execute("searchAnnouncements", "{\"q\":\"分红\"}")).thenReturn("观察");

    String observation =
        new ChannelToolExecutor(delegate, Channel.RAG)
            .execute("searchAnnouncements", "{\"q\":\"分红\"}");

    assertThat(observation).isEqualTo("观察");
    verify(delegate).execute("searchAnnouncements", "{\"q\":\"分红\"}");
  }

  @Test
  void disallowedToolRejectedWithoutTouchingDelegate() {
    ToolExecutor delegate = mock(ToolExecutor.class);

    String observation =
        new ChannelToolExecutor(delegate, Channel.RAG).execute("getDailyQuotes", "{}");

    // 错误观察喂回模型自我纠正（ToolRegistry 同款失败哲学），且不通达真实工具
    assertThat(observation).contains("getDailyQuotes").contains("不可用");
    verifyNoInteractions(delegate);
  }

  @Test
  void filteredSpecsKeepWhitelistOnly() {
    List<ToolRegistry.ToolSpec> filtered = ChannelToolExecutor.filteredSpecs(SPECS, Channel.STOCK);

    assertThat(filtered.stream().map(ToolRegistry.ToolSpec::name))
        .containsExactly("getDailyQuotes", "getFinancialSummary");
    assertThat(ChannelToolExecutor.filteredSpecs(SPECS, Channel.NONE)).isEmpty();
  }
}
