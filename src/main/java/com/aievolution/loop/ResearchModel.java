package com.aievolution.loop;

import java.util.List;

/** Loop 的模型侧端口（A10 IOP）：给定问题与研究历史，产出下一步决策。DeepSeek 适配在 #3 接入时落地。 */
public interface ResearchModel {

  ModelTurn nextTurn(String question, List<LoopStep> history);
}
