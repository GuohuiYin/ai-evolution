package com.aievolution.loop;

import java.util.List;

/**
 * 研究 Loop 的一次运行结果。
 *
 * @param answer 最终回答；{@link StopReason#MAX_STEPS} 时为 {@code null}——超限不编造答案， 措辞由调用方组织（复用工具上限
 *     RETURN_ERROR_RESPONSE 不击穿用户的思想）
 * @param steps 完整研究轨迹，顺序即执行序
 */
public record LoopResult(String answer, List<LoopStep> steps, StopReason stopReason) {}
