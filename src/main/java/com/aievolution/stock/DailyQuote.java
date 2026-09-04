package com.aievolution.stock;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 单日行情快照。
 *
 * @param source 数据源标识（红线 03：数字必须可溯源）
 * @param asOf 数据时点
 */
public record DailyQuote(
    String code, LocalDate date, BigDecimal close, String source, LocalDate asOf) {}
