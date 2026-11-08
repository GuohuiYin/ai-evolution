package com.aievolution.loop.temporal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** HitlMode 解析单测（W31 #2）：attended/unattended 大小写不敏感；空值落默认值守；非法值 fail-fast。 */
class HitlModeTest {

  @Test
  void parsesBothModesCaseInsensitively() {
    assertThat(HitlMode.parse("attended")).isEqualTo(HitlMode.ATTENDED);
    assertThat(HitlMode.parse("Unattended")).isEqualTo(HitlMode.UNATTENDED);
  }

  @Test
  void blankFallsBackToAttended() {
    assertThat(HitlMode.parse(null)).isEqualTo(HitlMode.ATTENDED);
    assertThat(HitlMode.parse("  ")).isEqualTo(HitlMode.ATTENDED);
  }

  @Test
  void illegalValueFailsFast() {
    assertThatThrownBy(() -> HitlMode.parse("auto"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("auto");
  }
}
