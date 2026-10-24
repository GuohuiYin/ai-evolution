package com.aievolution.loop.pipeline;

/**
 * 复核裁决（W22 #1d 消息契约之四）：Reviewer 的输出——通过 / 打回（带批注）。 打回批注回流 Builder 重写；打回耗尽（≤2 次仍不过）由编排层出拒答文案
 * （宁可不答不可错答，设计 §1.3）。
 *
 * <p>判别字段用 String 常量而非 sealed 层次（ModelTurnPayload 先例： 多态类型元数据跨 Temporal payload 边界不可靠）。
 */
public record ReviewVerdict(String kind, String comment) {

  public static final String PASS = "PASS";
  public static final String REJECT = "REJECT";

  public static ReviewVerdict pass() {
    return new ReviewVerdict(PASS, "");
  }

  public static ReviewVerdict reject(String comment) {
    return new ReviewVerdict(REJECT, comment);
  }

  /** 判别便利方法——@JsonIgnore：否则被 Jackson 当属性序列化进 payload，回程反序列化炸 */
  @com.fasterxml.jackson.annotation.JsonIgnore
  public boolean isPass() {
    return PASS.equals(this.kind);
  }
}
