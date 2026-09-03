package com.aievolution.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "对话响应")
public record ChatResponse(
    @Schema(description = "模型生成的回复内容") String reply,
    @Schema(description = "回复所依据的知识库引用（无引用说明模型未使用资料）") List<SourceDocument> sources) {}
