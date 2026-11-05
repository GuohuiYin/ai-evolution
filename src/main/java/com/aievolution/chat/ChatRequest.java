package com.aievolution.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "对话请求")
public record ChatRequest(
    @Schema(
            description = "发送给模型的用户消息，不允许为空，最长 2000 字符",
            example = "帮我对比贵州茅台和宁德时代的最新市盈率",
            maxLength = 2000)
        @NotBlank
        @Size(max = 2000)
        String message,
    @Schema(description = "会话标识（可选）：传入则续聊并共享会话记忆；不传则服务端生成新会话并在响应中回传", example = "conv-8f3a2b")
        String conversationId) {}
