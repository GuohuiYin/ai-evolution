package com.aievolution.eval;

/**
 * 生成评估裁判端口（约定 A10 面向接口）：评测方只依赖本契约， 实现可以是 LLM-as-a-Judge（{@link LlmEvalJudge}）或人工评审替身。
 *
 * <p>设计红线：judge 与被测模型必须是**两次独立调用**，评分逻辑不得混入业务 advisor 链。
 */
public interface EvalJudge {

  /** 三维评分（各 0-2 分）：准确性 / 遵循度 / 溯源完整性。 */
  record JudgeScore(int accuracy, int compliance, int grounding, String reason) {

    public JudgeScore {
      if (accuracy < 0
          || accuracy > 2
          || compliance < 0
          || compliance > 2
          || grounding < 0
          || grounding > 2) {
        throw new IllegalArgumentException("评分超界：各维度取值 0-2");
      }
    }

    public int total() {
      return accuracy + compliance + grounding;
    }
  }

  /**
   * 对一次被测回答评分。
   *
   * @throws IllegalStateException judge 输出不可解析时 fail-fast——评估事故不兜底打分
   */
  JudgeScore judge(GenerationCase generationCase, String answer);
}
