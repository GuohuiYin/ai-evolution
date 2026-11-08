package com.aievolution.loop.temporal;

/**
 * 一轮研究运行的活动超时预算档（W31 #1 failover 超时联动）：引擎启动 workflow 时按当前活跃模型臂 查表（{@code
 * ArmTimeoutProperties}）填入——换臂即换档；null = 沿用 workflow 内默认值（W22/W21 原值， 既有调用面与行为零变化）。
 *
 * @param quickActivitySeconds 轻活动预算（plan/review/单循环步，秒）
 * @param heavyActivitySeconds 重活动预算（build，秒；单循环 workflow 不使用）
 */
public record ActivityBudgets(long quickActivitySeconds, long heavyActivitySeconds) {}
