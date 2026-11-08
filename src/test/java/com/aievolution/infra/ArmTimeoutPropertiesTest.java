package com.aievolution.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** ArmTimeoutProperties 单测（W31 #1 步骤 2a）：查表命中、未知臂/缺档 fail-fast、yml 形态真实绑定。 */
class ArmTimeoutPropertiesTest {

  private static final ArmTimeoutProperties.ArmBudget PRIMARY =
      new ArmTimeoutProperties.ArmBudget(
          Duration.ofMinutes(2), Duration.ofMinutes(10), Duration.ofMinutes(3));
  private static final ArmTimeoutProperties.ArmBudget FALLBACK =
      new ArmTimeoutProperties.ArmBudget(
          Duration.ofMinutes(6), Duration.ofMinutes(20), Duration.ofMinutes(6));

  @Test
  void forArmReturnsConfiguredBudget() {
    var props = new ArmTimeoutProperties(PRIMARY, FALLBACK);

    assertThat(props.forArm("primary")).isEqualTo(PRIMARY);
    assertThat(props.forArm("fallback")).isEqualTo(FALLBACK);
  }

  @Test
  void unknownArmFailsFast() {
    var props = new ArmTimeoutProperties(PRIMARY, FALLBACK);

    assertThatThrownBy(() -> props.forArm("qwen"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("qwen");
  }

  @Test
  void missingArmSlotFailsFast() {
    assertThatThrownBy(() -> new ArmTimeoutProperties(PRIMARY, null).forArm("fallback"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("fallback");
  }

  @Test
  void yamlShapeBindsDurationsAndArms() {
    // application.yml 形态实绑（ISO-8601 Duration + 臂名键），防键名漂移静默失效
    new ApplicationContextRunner()
        .withUserConfiguration(RegisterProps.class)
        .withPropertyValues(
            "ai.model.arm-timeouts.primary.quick-activity-budget=PT2M",
            "ai.model.arm-timeouts.primary.heavy-activity-budget=PT10M",
            "ai.model.arm-timeouts.primary.http-read-timeout=PT3M",
            "ai.model.arm-timeouts.fallback.quick-activity-budget=PT6M",
            "ai.model.arm-timeouts.fallback.heavy-activity-budget=PT20M",
            "ai.model.arm-timeouts.fallback.http-read-timeout=PT6M")
        .run(
            context -> {
              ArmTimeoutProperties props = context.getBean(ArmTimeoutProperties.class);
              assertThat(props.forArm("primary")).isEqualTo(PRIMARY);
              assertThat(props.forArm("fallback")).isEqualTo(FALLBACK);
            });
  }

  @EnableConfigurationProperties(ArmTimeoutProperties.class)
  static class RegisterProps {}
}
