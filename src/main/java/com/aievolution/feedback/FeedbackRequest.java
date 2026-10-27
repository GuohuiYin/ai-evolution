package com.aievolution.feedback;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 反馈请求（W24 #3）：rating 仅 up/down；comment 可选（👎 时建议填，裁定依据）。 query 可选：页面捎上当轮问题原文——会话记忆里存的是 RAG 增强后的完整
 * prompt， 直接从记忆取「最后一条用户消息」会拿到带资料的巨 prompt，裁定台不可读。
 */
public record FeedbackRequest(
    @NotBlank String conversationId,
    @NotBlank @Pattern(regexp = "up|down", message = "rating 仅支持 up/down") String rating,
    String comment,
    String query) {}
