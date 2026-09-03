package com.aievolution.chat;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "引用来源")
public record SourceDocument(
    @Schema(description = "来源文件名", example = "maotai.md") String source,
    @Schema(description = "命中的资料片段") String excerpt) {}
