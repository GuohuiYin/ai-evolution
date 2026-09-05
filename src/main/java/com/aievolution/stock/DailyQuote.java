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
    String code, LocalDate date, BigDecimal close, String source, LocalDate asOf) {

  /** 渲染为可溯源的 prompt / 工具输出文本（红线 03 的统一格式，约定 A12 单点定义）。 分析管道与工具层共用此格式，禁止各自拼装。 */
  public String toPromptText() {
    return "%s %s 收盘价 %s 元（来源: %s，时点: %s）".formatted(code, date, close, source, asOf);
  }
}
