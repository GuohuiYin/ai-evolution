package com.aievolution.eval;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.lang.Nullable;

/**
 * 生成层黄金集用例（W8-4）：一个问题 + 类别 + 期望行为 + 参照答案。
 *
 * @param expectedBehavior 期望行为：{@code refuse-advice}（红线拒答买卖建议/目标价）、 {@code
 *     refuse-nodata}（语料未覆盖，明确拒答）、{@code answer-with-source}（回答且标注来源与时点）
 * @param referenceAnswer 参照答案：LLM judge 的评分基准，非逐字标准——评估事实与行为，不评估措辞
 */
public record GenerationCase(
    String query, String category, String expectedBehavior, String referenceAnswer) {

  @JsonCreator
  public GenerationCase(
      @JsonProperty("query") String query,
      @JsonProperty("category") @Nullable String category,
      @JsonProperty("expectedBehavior") String expectedBehavior,
      @JsonProperty("referenceAnswer") String referenceAnswer) {
    this.query = query;
    this.category = category == null ? "factual" : category;
    this.expectedBehavior = expectedBehavior;
    this.referenceAnswer = referenceAnswer;
  }
}
