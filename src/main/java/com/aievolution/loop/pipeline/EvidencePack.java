package com.aievolution.loop.pipeline;

import com.aievolution.loop.LoopStep;
import java.util.List;

/**
 * 证据包（W22 #1d 消息契约之二）：Builder 取证的原始结果+出处列表， 是 Reviewer 复核的唯一依据（Reviewer 只读本包，无工具—— 防复核者自己下场改证据，设计
 * §1.3）。
 */
public record EvidencePack(List<EvidenceItem> items) {

  public record EvidenceItem(String tool, String input, String observation) {}

  /** 从 Builder 的 ReAct 取证轨迹收口（工具名+入参即出处） */
  public static EvidencePack fromSteps(List<LoopStep> steps) {
    return new EvidencePack(
        steps.stream().map(s -> new EvidenceItem(s.tool(), s.input(), s.observation())).toList());
  }
}
