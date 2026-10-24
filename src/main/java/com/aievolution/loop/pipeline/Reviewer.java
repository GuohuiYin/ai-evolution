package com.aievolution.loop.pipeline;

/**
 * Reviewer 角色端口（W22 #1c，设计 §1.3）：独立于作者的语义复核—— 只读证据包，无工具（防复核者自己下场改证据）。兜底裁决：买卖建议/编造/
 * 溯源缺失一律打回。
 */
public interface Reviewer {

  /** 复核草稿：通过返回 {@link ReviewVerdict#pass()}，否则打回并附具体批注（供 Builder 重写） */
  ReviewVerdict review(String question, EvidencePack evidence, DraftAnswer draft);
}
