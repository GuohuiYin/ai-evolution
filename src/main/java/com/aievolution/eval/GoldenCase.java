package com.aievolution.eval;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.lang.Nullable;

/**
 * 黄金集用例：一个问题 + 期望命中的知识库来源 + 用例类别。
 *
 * @param expectSource 期望在 Top-K 内召回的来源文件名；{@code null} 表示负例——期望检索为空 （相似度阈值以下），是防幻觉闸门的回归用例
 * @param category 用例类别：{@code normal}（直给事实，CI 哈希向量替身可回归）、{@code boundary}（改写/间接问法，依赖真实语义向量）、{@code
 *     adversarial}（近域陷阱或越界问题，期望 不召回）
 */
public record GoldenCase(String query, @Nullable String expectSource, String category) {

  public GoldenCase(String query, @Nullable String expectSource) {
    this(query, expectSource, "normal");
  }

  @JsonCreator
  public GoldenCase(
      @JsonProperty("query") String query,
      @JsonProperty("expectSource") @Nullable String expectSource,
      @JsonProperty("category") @Nullable String category) {
    this.query = query;
    this.expectSource = expectSource;
    this.category = category == null ? "normal" : category;
  }
}
