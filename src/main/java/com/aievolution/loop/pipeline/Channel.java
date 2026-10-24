package com.aievolution.loop.pipeline;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** 取证通路（W22 #1d）：Planner 为每个子问题标注的唯一通路， 取值与 {@code prompts/planner-v1.md} 的输出约定一致（小写）。 */
public enum Channel {
  /** 年报/半年报等定期报告语料检索 */
  RAG,
  /** 行情/财务摘要工具 */
  STOCK,
  /** 公告检索工具 */
  ANNOUNCEMENT,
  /** 无需取证（红线类：直接拒答，不拆子问题） */
  NONE;

  @JsonValue
  public String toJson() {
    return name().toLowerCase(Locale.ROOT);
  }

  @JsonCreator
  public static Channel fromJson(String value) {
    if (value == null || value.isBlank()) {
      // 枚举外的值视同计划不可解析（严格好于猜）——由调用方走降级
      throw new IllegalArgumentException("未知取证通路: " + value);
    }
    return Channel.valueOf(value.trim().toUpperCase(Locale.ROOT));
  }
}
