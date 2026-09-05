package com.aievolution.rag;

import org.springframework.lang.Nullable;

/**
 * 知识检索的元数据过滤条件（领域类型）：按文档属性收窄召回范围，如"只查 2024 年报"。
 *
 * <p>字段为 {@code null} 表示该维度不过滤。不暴露 Spring AI 的 Filter API—— 底层向量库的过滤语法由 rag 域内部翻译（约定 A11-3）。
 *
 * @param docType 文档类型（report/announcement/note）
 * @param asOf 数据时点（ISO 日期，如 2024-12-31）
 */
public record KnowledgeFilter(@Nullable String docType, @Nullable String asOf) {

  /** 无过滤条件时的便捷常量。 */
  public static final KnowledgeFilter NONE = new KnowledgeFilter(null, null);
}
