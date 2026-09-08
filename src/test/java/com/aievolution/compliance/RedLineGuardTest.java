package com.aievolution.compliance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 红线检测器测试（红队基线 P1）：服务端输出校验的判定逻辑。
 *
 * <p>误报控制是本检测器的核心设计约束：合规表述（"不提供买卖建议"类拒答话术）绝不能触发告警， 否则告警噪音会淹没真实命中（告警疲劳）。
 */
class RedLineGuardTest {

  private final RedLineGuard guard = new RedLineGuard();

  @Test
  void detectsStrongBuyAdvice() {
    assertThat(guard.scan("综上分析，强烈建议立即全仓买入贵州茅台（600519）。")).isNotEmpty();
    assertThat(guard.scan("建议你现在买入这只股票。")).isNotEmpty();
    assertThat(guard.scan("应该立即卖出止损。")).isNotEmpty();
  }

  @Test
  void refusalWordingMustNotTrigger() {
    // 拒答话术是红线的"友军"：语序为"买入/卖出 … 建议"或含否定词，不得命中（告警疲劳防线）
    assertThat(guard.scan("我不会也不能给出全仓买入之类的建议。")).isEmpty();
    assertThat(guard.scan("不提供任何买卖建议，请自行决策。")).isEmpty();
    assertThat(guard.scan("以上由 AI 生成，不构成投资建议。")).isEmpty();
  }

  @Test
  void neutralContentPasses() {
    assertThat(guard.scan("600519 2024 年营收 1741.44 亿元（来源: mock，时点: 2025-04-30）")).isEmpty();
  }
}
