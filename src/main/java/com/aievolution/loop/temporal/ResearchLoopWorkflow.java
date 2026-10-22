package com.aievolution.loop.temporal;

import com.aievolution.loop.LoopResult;
import com.aievolution.loop.LoopStep;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import java.util.List;

/**
 * 研究循环的 Temporal workflow 端口（W21 #1② 路线 C）：{@code ResearchLoop} 的引擎对位物—— 同样的"模型决定 → 调工具 →
 * 再决定"串行语义，但 steps 状态驻留 workflow 内存， 事件历史即 checkpoint，崩溃重放续跑。
 */
@WorkflowInterface
public interface ResearchLoopWorkflow {

  @WorkflowMethod
  LoopResult runResearch(ResearchRunInput input);

  /** 当前已完成的步（运行中可查—— Temporal Query 语义：不重放、直读当前状态） */
  @QueryMethod
  List<LoopStep> currentSteps();

  /** 运行状态：not-started / running / synthesizing / completed */
  @QueryMethod
  String status();
}
