package com.aievolution.cost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.model.ChatResponse;

/** 预算熔断测试（W25 #2b）：fail-closed 三条命脉——未超放行、达线拒答且零调用、账本不可读同样拒答。 */
class BudgetGuardAdvisorTest {

  private static final Instant NOW = Instant.parse("2026-10-29T02:00:00Z");

  @TempDir Path tempDir;

  private TokenLedger ledger;
  private CallAdvisorChain chain;
  private final ChatClientRequest request = mock(ChatClientRequest.class);

  @BeforeEach
  void setUp() {
    ledger = new TokenLedger(tempDir.resolve("ledger.jsonl"), Clock.fixed(NOW, ZoneId.of("UTC")));
    chain = mock(CallAdvisorChain.class);
  }

  @Test
  void underBudgetProceeds() throws Exception {
    ledger.record("m", "rag", 100, 50, null);
    BudgetGuardAdvisor advisor = new BudgetGuardAdvisor(ledger, 200_000);
    ChatClientResponse expected = new ChatClientResponse(new ChatResponse(List.of()), Map.of());
    when(chain.nextCall(any())).thenReturn(expected);

    ChatClientResponse actual = advisor.adviseCall(request, chain);

    assertThat(actual).isSameAs(expected);
    verify(chain).nextCall(any());
  }

  @Test
  void atBudgetRejectsWithoutModelCall() {
    // 达线即拒（used >= budget）：150/150 不放行；关键是 chain.nextCall 从未发生——零真实模型调用
    ledger.record("m", "rag", 100, 50, null);
    BudgetGuardAdvisor advisor = new BudgetGuardAdvisor(ledger, 150);

    assertThatThrownBy(() -> advisor.adviseCall(request, chain))
        .isInstanceOf(BudgetExceededException.class)
        .hasMessageContaining("预算已用尽");
    verify(chain, never()).nextCall(any());
  }

  @Test
  void ledgerReadFailureRejectsFailClosed() throws Exception {
    // 账本读故障（目录冒充文件触发 IO 异常）→ 同样拒答：读不出账就无法证明未超支
    Path fakeLedger = tempDir.resolve("fake");
    Files.createDirectory(fakeLedger);
    TokenLedger brokenLedger = new TokenLedger(fakeLedger, Clock.fixed(NOW, ZoneId.of("UTC")));
    BudgetGuardAdvisor advisor = new BudgetGuardAdvisor(brokenLedger, 200_000);

    assertThatThrownBy(() -> advisor.adviseCall(request, chain))
        .isInstanceOf(BudgetExceededException.class)
        .hasMessageContaining("账本不可读");
    verify(chain, never()).nextCall(any());
  }
}
