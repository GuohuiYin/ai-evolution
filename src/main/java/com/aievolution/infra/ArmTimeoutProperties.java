package com.aievolution.infra;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 按模型臂的超时预算表（W31 #1 failover 超时联动）：超时预算从单值配置升级为按活跃臂查表—— 每臂一档延迟特征（quick=plan/review
 * 级轻活动预算，heavy=build 级重活动预算，httpRead=该臂 HTTP 读超时），换臂即换档。
 *
 * <p>臂位固化为 {@code primary}/{@code fallback} 两个显式字段（与 {@link ModelArmRegistry} 键一一对应）： 不用 Map——Map
 * 绑定缺档会静默成 null 等到运行时才爆，显式字段让缺档在绑定期即显形。
 *
 * <p>默认值取 W26 值守窗口实测（docs/ops/w26-unattended-run-report.md §六）：主力档=既有值（正常路径零变化）； 备用档按 Qwen thinking
 * 实账校准（单轮 3,025 token 跑穿 120s、241s 处 500，取 2 倍余量）。
 */
@ConfigurationProperties(prefix = "ai.model.arm-timeouts")
public record ArmTimeoutProperties(ArmBudget primary, ArmBudget fallback) {

  /** 单臂预算档：轻活动（plan/review/单循环步）/ 重活动（build）/ HTTP 读超时 */
  public record ArmBudget(
      Duration quickActivityBudget, Duration heavyActivityBudget, Duration httpReadTimeout) {}

  /** 查指定臂的预算档；未知臂名或档缺即配置错误，fail-fast 不静默（同 W29 端点角色表纪律） */
  public ArmBudget forArm(String arm) {
    ArmBudget budget =
        switch (arm) {
          case ModelArmRegistry.PRIMARY -> this.primary;
          case ModelArmRegistry.FALLBACK -> this.fallback;
          default ->
              throw new IllegalStateException("未知模型臂「" + arm + "」：臂名只允许 primary/fallback（W31 #1）");
        };
    if (budget == null) {
      throw new IllegalStateException(
          "ai.model.arm-timeouts 缺臂位档「" + arm + "」：按臂查表不允许静默缺档（W31 #1）");
    }
    return budget;
  }
}
