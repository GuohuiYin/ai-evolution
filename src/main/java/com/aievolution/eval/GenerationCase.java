package com.aievolution.eval;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.springframework.lang.Nullable;

/**
 * 生成层黄金集用例（W8-4）：一个问题 + 类别 + 期望行为 + 参照答案。 W11 #6 起支持多轮脚本（{@code turns}）。
 *
 * @param expectedBehavior 期望行为：{@code refuse-advice}（红线拒答买卖建议/目标价）、 {@code
 *     refuse-nodata}（语料未覆盖，明确拒答）、{@code answer-with-source}（回答且标注来源与时点）
 * @param referenceAnswer 参照答案：LLM judge 的评分基准，非逐字标准——评估事实与行为，不评估措辞
 * @param turns 多轮脚本（可空）：非空时按序在同一会话执行，{@code query} 为被评的末轮提问， judge 只评末轮回答；单轮用例该字段缺省
 */
public record GenerationCase(
    String query,
    String category,
    String expectedBehavior,
    String referenceAnswer,
    List<String> turns) {

  @JsonCreator
  public GenerationCase(
      @JsonProperty("query") String query,
      @JsonProperty("category") @Nullable String category,
      @JsonProperty("expectedBehavior") String expectedBehavior,
      @JsonProperty("referenceAnswer") String referenceAnswer,
      @JsonProperty("turns") @Nullable List<String> turns) {
    this.query = query;
    this.category = category == null ? "factual" : category;
    this.expectedBehavior = expectedBehavior;
    this.referenceAnswer = referenceAnswer;
    this.turns = turns;
  }

  /** 单轮用例构造（W8 兼容形态） */
  public GenerationCase(
      String query, String category, String expectedBehavior, String referenceAnswer) {
    this(query, category, expectedBehavior, referenceAnswer, null);
  }

  public boolean isMultiTurn() {
    return turns != null && turns.size() > 1;
  }
}
