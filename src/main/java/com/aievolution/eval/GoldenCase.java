package com.aievolution.eval;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.springframework.lang.Nullable;

/**
 * 黄金集用例：一个问题 + 期望命中的知识库来源集合 + 用例类别。
 *
 * @param expectSources 期望在 Top-K 内召回的来源文件名集合，任一命中即通过（多源语料下同一问题 可能有多个正确答案来源）；{@code null}
 *     表示负例——期望检索为空（相似度阈值以下）， 是防幻觉闸门的回归用例
 * @param category 用例类别：{@code normal}（直给事实，CI 哈希向量替身可回归）、{@code boundary}（改写/间接问法，依赖真实语义向量）、{@code
 *     paraphrase}（同义换问敏感性，真实语义向量）、{@code literal}（关键词/原文片段式查询，真实语义向量； 用于评估混合检索的潜在价值）、{@code
 *     adversarial}（近域陷阱或越界问题，期望 不召回）。注：in-domain-unanswerable（话题相关但语料未覆盖，应拒答）不在本文件——
 *     检索层命中对它而言是合理行为，拒答判定属生成层黄金集（W8-4）
 */
public record GoldenCase(String query, @Nullable List<String> expectSources, String category) {

  public GoldenCase(String query, @Nullable String expectSource) {
    this(query, expectSource == null ? null : List.of(expectSource), "normal");
  }

  @JsonCreator
  public GoldenCase(
      @JsonProperty("query") String query,
      @JsonProperty("expectSources") @Nullable List<String> expectSources,
      @JsonProperty("category") @Nullable String category) {
    this.query = query;
    this.expectSources = expectSources;
    this.category = category == null ? "normal" : category;
  }
}
