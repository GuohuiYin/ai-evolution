package com.aievolution.chat;

import java.util.List;

/**
 * P0 个股历史表现分析的结构化输出（schema 即 API 契约）。
 *
 * @param performanceSummary 总体表现概述（仅历史事实，不含预测）
 * @param highlights 亮点（每条数字须含来源与时点）
 * @param riskPoints 风险点
 * @param sources 所用数据的来源与时点清单（红线 03）
 */
public record StockAnalysisReport(
    String code,
    String performanceSummary,
    List<String> highlights,
    List<String> riskPoints,
    List<String> sources) {}
