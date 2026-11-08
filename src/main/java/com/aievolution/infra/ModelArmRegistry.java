package com.aievolution.infra;

/**
 * 活跃模型臂查询（W31 #1）：failover 超时联动的判定依据——编排层据此按臂查超时预算档。
 *
 * <p>臂名是稳定键（不随具体模型变更）：{@value #PRIMARY} / {@value #FALLBACK}，与配置表 {@code
 * ai.model.arm-timeouts.<臂名>} 对应。实现：{@link FailoverChatModel}——切换发生后进入冷却窗，
 * 窗口内报备用臂，窗口过后报主力臂（调用路径不变，W12 切换语义不动）。
 */
public interface ModelArmRegistry {

  String PRIMARY = "primary";
  String FALLBACK = "fallback";

  /** 当前活跃臂：{@value #PRIMARY} 或 {@value #FALLBACK} */
  String activeArm();
}
