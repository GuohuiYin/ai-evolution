package com.aievolution.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.aievolution.cost.CostChannel;
import com.aievolution.cost.TokenLedger;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.deepseek.api.DeepSeekApi;

/** token 成本观测测试：每次模型调用的用量账必须留痕（W7-Step2，成本治理的度量基座）。 */
class TokenUsageAdvisorTest {

  @TempDir Path tempDir;

  private TokenLedger ledger;
  private TokenUsageAdvisor advisor;
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void attachAppender() {
    ledger = new TokenLedger(tempDir.resolve("ledger.jsonl"), Clock.systemDefaultZone());
    advisor = new TokenUsageAdvisor(ledger);
    ch.qos.logback.classic.Logger logger =
        (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(TokenUsageAdvisor.class);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @Test
  void logsTokenUsageFromResponseMetadata() {
    ChatResponse response =
        new ChatResponse(
            List.of(new Generation(new AssistantMessage("回答"))),
            ChatResponseMetadata.builder()
                .model("deepseek-v4-flash")
                .usage(new DefaultUsage(1234, 56))
                .build());
    CallAdvisorChain chain = mock(CallAdvisorChain.class);
    when(chain.nextCall(any())).thenReturn(new ChatClientResponse(response, Map.of()));

    advisor.adviseCall(requestWithChannel("rag"), chain);

    assertThat(appender.list)
        .anySatisfy(
            e ->
                assertThat(e.getFormattedMessage())
                    .contains("stage=MODEL")
                    .contains("model=deepseek-v4-flash")
                    .contains("tokens_in=1234")
                    .contains("tokens_out=56"));
  }

  @Test
  void missingUsageTolerated() {
    // 部分供应商/流式场景无 usage：记 -- 而非抛错，观测不能破坏业务
    ChatResponse response =
        new ChatResponse(
            List.of(new Generation(new AssistantMessage("回答"))),
            ChatResponseMetadata.builder().build());
    CallAdvisorChain chain = mock(CallAdvisorChain.class);
    when(chain.nextCall(any())).thenReturn(new ChatClientResponse(response, Map.of()));

    advisor.adviseCall(requestWithChannel("rag"), chain);

    assertThat(appender.list)
        .anySatisfy(e -> assertThat(e.getFormattedMessage()).contains("tokens_in=--"));
  }

  @Test
  void deepSeekCachedTokensPromotedToCacheRead() {
    // DeepSeek 缓存命中在原生 usage 的 promptTokensDetails.cachedTokens（框架未标准化映射），
    // 账本须将其提升为一等键 cache_read，否则缓存收益不可见
    ChatResponse response =
        new ChatResponse(
            List.of(new Generation(new AssistantMessage("回答"))),
            ChatResponseMetadata.builder()
                .usage(
                    new DefaultUsage(
                        965,
                        97,
                        1062,
                        new DeepSeekApi.Usage(
                            97, 965, 1062, new DeepSeekApi.Usage.PromptTokensDetails(896))))
                .build());
    CallAdvisorChain chain = mock(CallAdvisorChain.class);
    when(chain.nextCall(any())).thenReturn(new ChatClientResponse(response, Map.of()));

    advisor.adviseCall(requestWithChannel("rag"), chain);

    assertThat(appender.list)
        .anySatisfy(e -> assertThat(e.getFormattedMessage()).contains("cache_read=896"));
  }

  @Test
  void recordsToLedgerWithChannelTag() {
    // W25 #2a-ii：上报用量必须落台账（#2b 熔断的数据源），通路标记随请求上下文进账
    ChatResponse response =
        new ChatResponse(
            List.of(new Generation(new AssistantMessage("回答"))),
            ChatResponseMetadata.builder()
                .model("deepseek-v4-flash")
                .usage(new DefaultUsage(1234, 56))
                .build());
    CallAdvisorChain chain = mock(CallAdvisorChain.class);
    when(chain.nextCall(any())).thenReturn(new ChatClientResponse(response, Map.of()));

    advisor.adviseCall(requestWithChannel("rag"), chain);

    TokenLedger.DailySummary summary = ledger.summarize(LocalDate.now());
    assertThat(summary.calls()).isEqualTo(1);
    assertThat(summary.promptTokens()).isEqualTo(1234);
    assertThat(summary.byChannel()).containsKey("rag");
    assertThat(summary.byChannel().get("rag")[0]).isEqualTo(1290);
  }

  @Test
  void unreportedUsageNotRecorded() {
    // 供应商未上报时不落账：台账只记真实发生的调用，不记占位 0
    ChatResponse response =
        new ChatResponse(
            List.of(new Generation(new AssistantMessage("回答"))),
            ChatResponseMetadata.builder().build());
    CallAdvisorChain chain = mock(CallAdvisorChain.class);
    when(chain.nextCall(any())).thenReturn(new ChatClientResponse(response, Map.of()));

    advisor.adviseCall(requestWithChannel("rag"), chain);

    assertThat(ledger.summarize(LocalDate.now()).calls()).isZero();
  }

  private static ChatClientRequest requestWithChannel(String channel) {
    ChatClientRequest request = mock(ChatClientRequest.class);
    when(request.context()).thenReturn(Map.of(CostChannel.CONTEXT_KEY, channel));
    return request;
  }
}
