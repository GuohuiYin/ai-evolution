package com.aievolution.loop.temporal;

import java.util.List;

/**
 * 一轮研究运行的 workflow 入参（W21 #1②）。
 *
 * @param question 用户问题
 * @param maxSteps 步数上限（引擎装配侧已校验 ≥1，同 ResearchLoop 构造语义）
 * @param history 会话历史（压平载体）
 * @param traceId 全链路追踪 ID——activity 侧重建 MDC，研究轨迹日志不断链
 * @param hitlEnabled HITL 断点开关（W23 #1，裁决①B：只接「打回耗尽升级人工」一条触发）； 单循环 workflow 忽略
 * @param hitlAwaitSeconds 审批等待上限（秒），超时未决 fail-closed 拒答（宁可不答不可错答）
 * @param budgets 活动超时预算档（W31 #1，按活跃模型臂查表结果）；null = workflow 内默认值
 * @param hitlMode HITL 模式（W31 #2）：值守=挂起等人工；无人=不挂起立即 fail-closed 拒答
 */
public record ResearchRunInput(
    String question,
    int maxSteps,
    List<HistoryTurn> history,
    String traceId,
    boolean hitlEnabled,
    long hitlAwaitSeconds,
    ActivityBudgets budgets,
    HitlMode hitlMode) {

  /** HITL 关闭的便捷构造（单循环与既有调用面不变） */
  public ResearchRunInput(
      String question, int maxSteps, List<HistoryTurn> history, String traceId) {
    this(question, maxSteps, history, traceId, false, 0, null, HitlMode.ATTENDED);
  }

  /** 无预算档的便捷构造（W31 #1 前调用面兼容：默认值由 workflow 内常量承担） */
  public ResearchRunInput(
      String question,
      int maxSteps,
      List<HistoryTurn> history,
      String traceId,
      boolean hitlEnabled,
      long hitlAwaitSeconds) {
    this(
        question,
        maxSteps,
        history,
        traceId,
        hitlEnabled,
        hitlAwaitSeconds,
        null,
        HitlMode.ATTENDED);
  }

  /** 带预算档、值守模式（默认）的便捷构造 */
  public ResearchRunInput(
      String question,
      int maxSteps,
      List<HistoryTurn> history,
      String traceId,
      boolean hitlEnabled,
      long hitlAwaitSeconds,
      ActivityBudgets budgets) {
    this(
        question,
        maxSteps,
        history,
        traceId,
        hitlEnabled,
        hitlAwaitSeconds,
        budgets,
        HitlMode.ATTENDED);
  }
}
