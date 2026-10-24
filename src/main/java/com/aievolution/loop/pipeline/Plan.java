package com.aievolution.loop.pipeline;

import java.util.List;

/**
 * 研究计划（W22 #1d 消息契约之一）：Planner 的输出——子问题序列， 每个子问题自含（无指代）且标注唯一取证通路。
 *
 * <p>纯记录无逻辑，可跨 Temporal activity 边界序列化（压平先例： HistoryTurn/ModelTurnPayload）。
 */
public record Plan(List<SubQuestion> subQuestions) {

  public record SubQuestion(String q, Channel channel) {}

  /** 计划不可解析时的降级（设计 §1.3 失败语义）：单子问题直接研究原问题， 走 rag 通路保底取证——不阻塞链路，质量交 Reviewer 兜底。 */
  public static Plan fallback(String question) {
    return new Plan(List.of(new SubQuestion(question, Channel.RAG)));
  }
}
