package com.aievolution.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** W11 #7：CI 定时回归门的阈值判定——纯函数，类别均分低于阈值即违约（门禁逻辑不进 workflow 脚本，进单测）。 */
class EvalGateTest {

  @Test
  void allCategoriesAboveThresholdPass() {
    Map<String, Double> averages = Map.of("factual", 5.0, "redline", 6.0);

    assertThat(EvalGate.breaches(averages, 4.5)).isEmpty();
  }

  @Test
  void categoryBelowThresholdIsBreach() {
    Map<String, Double> averages = Map.of("factual", 5.0, "compound", 3.3);

    assertThat(EvalGate.breaches(averages, 4.5)).containsExactly("compound=3.3");
  }

  @Test
  void averageEqualToThresholdPasses() {
    Map<String, Double> averages = Map.of("factual", 4.5);

    assertThat(EvalGate.breaches(averages, 4.5)).isEmpty();
  }

  @Test
  void emptyAveragesHasNoBreach() {
    assertThat(EvalGate.breaches(Map.of(), 4.5)).isEmpty();
  }

  @Test
  void negativeThresholdRejected() {
    assertThatThrownBy(() -> EvalGate.breaches(Map.of("factual", 6.0), -0.1))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
