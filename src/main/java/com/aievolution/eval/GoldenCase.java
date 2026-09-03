package com.aievolution.eval;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.lang.Nullable;

/**
 * 黄金集用例：一个问题 + 期望命中的知识库来源。
 *
 * @param expectSource 期望 Top-1 命中的来源文件名；{@code null} 表示负例——期望检索为空 （相似度阈值以下），是防幻觉闸门的回归用例
 */
public record GoldenCase(String query, @Nullable String expectSource) {

  @JsonCreator
  public GoldenCase(
      @JsonProperty("query") String query, @JsonProperty("expectSource") String expectSource) {
    this.query = query;
    this.expectSource = expectSource;
  }
}
