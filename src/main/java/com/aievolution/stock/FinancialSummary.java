package com.aievolution.stock;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 年度财务摘要。金额单位：亿元人民币。
 *
 * @param source 数据源标识（红线 03：数字必须可溯源）
 * @param asOf 数据时点
 */
public record FinancialSummary(
    String code,
    int fiscalYear,
    BigDecimal revenue,
    BigDecimal netProfit,
    String source,
    LocalDate asOf) {}
