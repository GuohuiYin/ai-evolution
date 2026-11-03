package com.aievolution.cost;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 日预算运行时调整测试（W27 #4）：当日生效、重启回放、次日回落、损坏从严回落——四条命脉对应 W26 报告 P5（调预算须重启）的修复语义（Owner 2026-11-03 拍板当日有效）。
 */
class DailyBudgetOverrideTest {

  private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
  private static final Instant NOW = Instant.parse("2026-11-03T02:00:00Z"); // 当日 10:00 CST
  private static final Instant YESTERDAY = Instant.parse("2026-11-02T02:00:00Z");

  @TempDir Path tempDir;

  private DailyBudgetOverride newOverride(long defaultBudget, Clock clock) {
    return new DailyBudgetOverride(defaultBudget, tempDir.resolve("budget-override.json"), clock);
  }

  @Test
  void noAdjustmentUsesConfiguredDefault() {
    DailyBudgetOverride override = newOverride(200_000, Clock.fixed(NOW, ZONE));

    assertThat(override.effectiveBudget()).isEqualTo(200_000);
    assertThat(override.currentAdjustment()).isNull();
  }

  @Test
  void adjustTakesEffectImmediatelyAndPersistsTrace() throws Exception {
    DailyBudgetOverride override = newOverride(200_000, Clock.fixed(NOW, ZONE));

    override.adjust(8_000_000, "撞线救场");

    assertThat(override.effectiveBudget()).isEqualTo(8_000_000);
    assertThat(override.currentAdjustment().reason()).isEqualTo("撞线救场");
    String persisted = Files.readString(tempDir.resolve("budget-override.json"));
    assertThat(persisted).contains("8000000").contains("撞线救场").contains("2026-11-03");
  }

  @Test
  void adjustmentSurvivesRestartWithinSameDay() {
    Clock clock = Clock.fixed(NOW, ZONE);
    newOverride(200_000, clock).adjust(8_000_000, "撞线救场");

    // 模拟重启：同路径同时钟新建实例，回放当日调整值
    DailyBudgetOverride afterRestart = newOverride(200_000, clock);

    assertThat(afterRestart.effectiveBudget()).isEqualTo(8_000_000);
  }

  @Test
  void yesterdaysAdjustmentExpiresToDefault() {
    newOverride(200_000, Clock.fixed(YESTERDAY, ZONE)).adjust(8_000_000, "昨日救场");

    // 次日启动：调整值过期，自动回落配置默认
    DailyBudgetOverride nextDay = newOverride(200_000, Clock.fixed(NOW, ZONE));

    assertThat(nextDay.effectiveBudget()).isEqualTo(200_000);
    assertThat(nextDay.currentAdjustment()).isNull();
  }

  @Test
  void corruptFileFallsBackToDefault() throws Exception {
    Files.writeString(tempDir.resolve("budget-override.json"), "{not json");

    DailyBudgetOverride override = newOverride(200_000, Clock.fixed(NOW, ZONE));

    assertThat(override.effectiveBudget()).isEqualTo(200_000);
  }
}
