package com.aievolution.loop.temporal;

import com.aievolution.loop.LoopStep;
import io.temporal.activity.ActivityInterface;
import java.util.List;

/**
 * 研究循环的 Temporal activity 端口（W21 #1② 路线 C）：一切副作用（模型调用/工具执行/观测回调） 的唯一出口——workflow
 * 本体保持确定性可重放，全靠本接口与外界交互。
 *
 * <p>三方法对应 {@code ResearchLoop} 骨架的三类副作用：模型决策 / 触顶合成 / 工具执行+留痕。
 */
@ActivityInterface
public interface ResearchLoopActivities {

  /** 模型一步决策（内部经 DeepSeekResearchModel：prompt 渲染 + 协议解析 + 违背计数） */
  ModelTurnPayload nextTurn(
      String question, List<LoopStep> steps, List<HistoryTurn> history, String traceId);

  /** 触顶无工具合成（W20 #1 裁决①B 语义）；合成失败返回空串——null payload 不跨边界 */
  String synthesize(
      String question, List<LoopStep> steps, List<HistoryTurn> history, String traceId);

  /** 执行一步工具调用并留痕：research-trace 日志（与 inline 引擎同格式）+ LoopListener 逐步回调 */
  LoopStep executeStep(int index, String thought, String tool, String input, String traceId);
}
