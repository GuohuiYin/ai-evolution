package com.aievolution.loop.temporal;

import com.aievolution.loop.pipeline.Plan;
import com.aievolution.loop.pipeline.ReviewVerdict;
import io.temporal.activity.ActivityInterface;
import java.util.List;

/**
 * 三角色管线的 Temporal activity 端口（W22 #1c）：每角色一个 activity—— Planner / Builder / Reviewer
 * 的全部副作用（模型调用/工具执行） 的唯一出口，workflow 本体保持确定性可重放（ResearchLoopActivities 同款纪律）。
 */
@ActivityInterface
public interface ResearchPipelineActivities {

  /** Planner：拆解研究计划（无工具；不可解析由实现方降级保底，不抛异常阻塞链路） */
  Plan plan(String question, List<HistoryTurn> history, String traceId);

  /** Builder：按计划逐子问题取证（通路白名单）+ 起草终答。 {@code reviewComment} 非空 = 上轮打回批注，回流重写（设计 §1.3）。 */
  BuildOutcome build(
      String question,
      Plan plan,
      List<HistoryTurn> history,
      String reviewComment,
      int maxSteps,
      String traceId);

  /** Reviewer：只读证据包独立复核（无工具）；输出不可解析由实现方 fail-closed 打回 */
  ReviewVerdict review(String question, BuildOutcome outcome, String traceId);
}
