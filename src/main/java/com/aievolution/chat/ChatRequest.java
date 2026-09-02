package com.aievolution.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "对话请求")
public record ChatRequest(
    @Schema(description = "发送给模型的用户消息，不允许为空", example = "帮我对比贵州茅台和宁德时代的最新市盈率") @NotBlank
        String message) {}
