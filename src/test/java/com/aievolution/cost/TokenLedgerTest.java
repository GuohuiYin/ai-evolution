package com.aievolution.cost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@link TokenLedger} 单测（W25 #2a）：追加-聚合往返 / 界日过滤 / 维度分桶 / 缺文件与坏行降级。 */
class TokenLedgerTest {

  private static final ZoneId ZONE = ZoneOffset.UTC;
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 29);

  @TempDir Path dir;

  private TokenLedger ledgerAt(String instant) {
    // 固定时钟：record 打的时间戳即测试指定的「今天」
    return new TokenLedger(dir.resolve("ledger.jsonl"), Clock.fixed(Instant.parse(instant), ZONE));
  }

  @Test
  void recordThenSummarizeRoundTrip() {
    TokenLedger ledger = ledgerAt("2026-10-29T02:00:00Z");
    ledger.record("deepseek-flash", "rag", 1000, 200, null);
    ledger.record("deepseek-flash", "agent", 500, 100, 300L);
    ledger.record("deepseek-v4-pro", "agent", 2000, 400, null);

    TokenLedger.DailySummary s = ledger.summarize(TODAY);

    assertThat(s.calls()).isEqualTo(3);
    assertThat(s.promptTokens()).isEqualTo(3500);
    assertThat(s.completionTokens()).isEqualTo(700);
    assertThat(s.cacheReadTokens()).isEqualTo(300);
    assertThat(s.byModel().get("deepseek-flash")).containsExactly(1800L, 2L);
    assertThat(s.byModel().get("deepseek-v4-pro")).containsExactly(2400L, 1L);
    assertThat(s.byChannel().get("rag")).containsExactly(1200L, 1L);
    assertThat(s.byChannel().get("agent")).containsExactly(3000L, 2L);
  }

  @Test
  void summaryFiltersByDay() {
    TokenLedger ledger = ledgerAt("2026-10-29T02:00:00Z");
    ledger.record("m", "rag", 100, 10, null);
    // 换一天写（同一文件，时钟换日）
    TokenLedger yesterday =
        new TokenLedger(
            dir.resolve("ledger.jsonl"), Clock.fixed(Instant.parse("2026-10-28T23:00:00Z"), ZONE));
    yesterday.record("m", "rag", 999, 99, null);

    TokenLedger.DailySummary today = ledger.summarize(TODAY);
    assertThat(today.calls()).isEqualTo(1);
    assertThat(today.promptTokens()).isEqualTo(100);
    assertThat(ledger.summarize(LocalDate.of(2026, 10, 28)).calls()).isEqualTo(1);
  }

  @Test
  void missingLedgerIsEmptyAccount() {
    TokenLedger.DailySummary s = ledgerAt("2026-10-29T02:00:00Z").summarize(TODAY);
    assertThat(s.calls()).isZero();
    assertThat(s.promptTokens()).isZero();
  }

  @Test
  void corruptLineSkippedNotFatal() throws Exception {
    TokenLedger ledger = ledgerAt("2026-10-29T02:00:00Z");
    ledger.record("m", "rag", 100, 10, null);
    Files.writeString(
        dir.resolve("ledger.jsonl"), "{半行残写\n", java.nio.file.StandardOpenOption.APPEND);

    TokenLedger.DailySummary s = ledger.summarize(TODAY);
    assertThat(s.calls()).isEqualTo(1);
  }

  @Test
  void readFailurePropagatesForFailClosedCaller() {
    // 目录冒充文件制造 IO 故障：读路径必须抛出（#2b 熔断按此 fail-closed）
    TokenLedger ledger =
        new TokenLedger(dir, Clock.fixed(Instant.parse("2026-10-29T02:00:00Z"), ZONE));
    assertThatThrownBy(() -> ledger.summarize(TODAY)).isInstanceOf(UncheckedIOException.class);
  }
}
