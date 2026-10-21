package com.aievolution.loop;

import java.util.List;
import java.util.Optional;

/** Loop 的模型侧端口（A10 IOP）：给定问题与研究历史，产出下一步决策。DeepSeek 适配在 #3 接入时落地。 */
public interface ResearchModel {

  ModelTurn nextTurn(String question, List<LoopStep> history);

  /**
   * 无工具合成步（W20 #1 裁决①B）：MAX_STEPS 触顶时，把已收集的 observations 喂回模型强制收尾。
   * 诚实红线绑定——仅基于已查证信息，不足则如实声明；合成失败（空内容）返回空，调用方退兜底文案。
   *
   * <p>默认实现=不支持合成（安全降级：触顶即空手退兜底）——保持端口为函数式接口， 测试桩/简易适配不必为可选能力买单；具备合成能力的适配（DeepSeekResearchModel）覆写。
   */
  default Optional<String> synthesize(String question, List<LoopStep> history) {
    return Optional.empty();
  }
}
