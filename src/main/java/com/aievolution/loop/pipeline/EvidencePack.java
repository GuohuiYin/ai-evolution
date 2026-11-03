package com.aievolution.loop.pipeline;

import com.aievolution.loop.LoopStep;
import java.util.List;

/**
 * 证据包（W22 #1d 消息契约之二）：Builder 取证的原始结果+出处列表， 是 Reviewer 复核的唯一依据（Reviewer 只读本包，无工具—— 防复核者自己下场改证据，设计
 * §1.3）。
 *
 * <p>W27 #2a：会话上下文（{@code contextTurns}，最近 N 轮问答原文）随包入复核—— 追问场景（"那 600519 呢"）Reviewer
 * 看不到上文会把正确承接的证据判编造打回（W26 多轮拒答 57% 根因）； 上下文进包而非进 Reviewer 入参，保住"只读本包"的单一事实源。
 */
public record EvidencePack(List<EvidenceItem> items, List<String> contextTurns) {

  public record EvidenceItem(String tool, String input, String observation) {}

  /** 从 Builder 的 ReAct 取证轨迹收口（工具名+入参即出处），无会话上下文（首轮/测试便捷） */
  public static EvidencePack fromSteps(List<LoopStep> steps) {
    return fromSteps(steps, List.of());
  }

  /** 取证轨迹 + 会话上下文（已渲染为「用户：…/助手：…」行，渲染方负责截断） */
  public static EvidencePack fromSteps(List<LoopStep> steps, List<String> contextTurns) {
    return new EvidencePack(
        steps.stream().map(s -> new EvidenceItem(s.tool(), s.input(), s.observation())).toList(),
        List.copyOf(contextTurns));
  }
}
