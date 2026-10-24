package com.aievolution.loop.temporal;

import com.aievolution.loop.LoopResult;
import com.aievolution.loop.LoopStep;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import java.util.List;

/**
 * 三角色管线的 Temporal workflow 端口（W22 #1c）：串行管线 {@code Planner → Builder → Reviewer →（打回 ≤2 次）→ 终答}，
 * 与单循环 workflow 共用 {@link ResearchRunInput}/{@link LoopResult} 调用面（引擎可互换， inline 单循环留对照臂）。
 *
 * <p>HITL 预埋（W22 #1f）：{@link #approve} Signal 为 Reviewer 通过后、 终答发出前的人工审批断点位——本周直透不挂起（W23 接通，
 * 触发策略三条在案：红线擦边 / 低置信证据 / 打回耗尽升级人工）。
 */
@WorkflowInterface
public interface ResearchPipelineWorkflow {

  @WorkflowMethod
  LoopResult runResearch(ResearchRunInput input);

  /** 当前已完成的步（Temporal Query：不重放、直读当前状态） */
  @QueryMethod
  List<LoopStep> currentSteps();

  /** 运行状态：not-started / running / reviewing / completed */
  @QueryMethod
  String status();

  /** HITL 人工放行 Signal（W23 接通；本周 workflow 不挂起，信号到达即存档） */
  @SignalMethod
  void approve(String comment);

  /** 是否正挂起等人工放行（本周恒 false——直透） */
  @QueryMethod
  boolean pendingApproval();
}
