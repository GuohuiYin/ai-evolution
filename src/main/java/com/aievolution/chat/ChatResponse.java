package com.aievolution.chat;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "对话响应")
public record ChatResponse(@Schema(description = "模型生成的回复内容") String reply) {}
