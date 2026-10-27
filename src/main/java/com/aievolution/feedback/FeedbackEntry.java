package com.aievolution.feedback;

/**
 * 一条人工反馈（W24 #3 最小环）：👎 登记为 badcase（PENDING_REVIEW，回流黄金集必须人工裁定—— Owner 裁决②A，造数规范四纪律不变）；👍
 * 仅留痕（NOTED）。userMessage/assistantAnswer 是提交时刻 会话最后一条问答快照，供裁定与造数时还原上下文。
 */
public record FeedbackEntry(
    String id,
    String conversationId,
    String rating,
    String comment,
    String userMessage,
    String assistantAnswer,
    String status,
    String createdAt) {

  public static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
  public static final String STATUS_NOTED = "NOTED";
  public static final String STATUS_APPROVED = "APPROVED";
  public static final String STATUS_REJECTED = "REJECTED";

  public FeedbackEntry withStatus(String newStatus) {
    return new FeedbackEntry(
        id, conversationId, rating, comment, userMessage, assistantAnswer, newStatus, createdAt);
  }
}
