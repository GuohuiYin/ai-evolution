package com.aievolution.loop.temporal;

import com.aievolution.loop.ModelTurn;

/**
 * {@link ModelTurn} 的 JSON 友好载体（W21 #1②）：sealed 层次跨 Temporal activity 边界 序列化不可靠（多态类型元数据），压平为带 kind
 * 判别字段的纯记录。
 */
public record ModelTurnPayload(
    String kind, String thought, String answer, String tool, String input) {

  public static final String FINAL = "FINAL";
  public static final String ACT = "ACT";

  public static ModelTurnPayload ofFinal(String thought, String answer) {
    return new ModelTurnPayload(FINAL, thought, answer, null, null);
  }

  public static ModelTurnPayload ofAct(String thought, String tool, String input) {
    return new ModelTurnPayload(ACT, thought, null, tool, input);
  }

  public static ModelTurnPayload from(ModelTurn turn) {
    return switch (turn) {
      case ModelTurn.Final f -> ofFinal(f.thought(), f.answer());
      case ModelTurn.Act a -> ofAct(a.thought(), a.tool(), a.input());
    };
  }

  /** 判别便利方法——@JsonIgnore：否则被 Jackson 当属性序列化进 payload，回程反序列化炸 */
  @com.fasterxml.jackson.annotation.JsonIgnore
  public boolean isFinal() {
    return FINAL.equals(this.kind);
  }
}
