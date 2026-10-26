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
 * <p>HITL（W23 #1 接通，裁决①B）：「打回耗尽」为唯一启用触发——复核 {@code MAX_REJECTIONS+1} 轮仍不过时挂起等人工 Signal（{@link
 * #approve} 放行 / {@link #reject} 驳回 / 超时缺席拒答）； 红线擦边、低置信证据两条策略埋点留待后续裁决。
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

  /** HITL 人工放行 Signal（W23 #1 接通：打回耗尽挂起后，放行=以审批人背书放出最后草稿） */
  @SignalMethod
  void approve(String comment);

  /** HITL 人工驳回 Signal（W23 #1：驳回=维持拒答，fail-closed） */
  @SignalMethod
  void reject(String comment);

  /** 是否正挂起等人工决定（W23 #1 起为真实状态：打回耗尽挂起期间 true） */
  @QueryMethod
  boolean pendingApproval();
}
