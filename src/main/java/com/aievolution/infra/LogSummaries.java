package com.aievolution.infra;

/**
 * 日志摘要策略（单点定义，A11「两次即收口」首例——原 ToolAuditAspect 与 ResearchLoop 各持一份）： 空白归一 +
 * 超长截断，防多行/超长文本污染日志行（一行一事件，检索与审计都依赖行的原子性）。
 */
public final class LogSummaries {

  /** 摘要最大长度：超出部分以「…」收尾 */
  public static final int MAX_LENGTH = 100;

  private LogSummaries() {}

  public static String summarize(Object value) {
    if (value == null) {
      return "";
    }
    String normalized = String.valueOf(value).replaceAll("\\s+", " ");
    return normalized.length() <= MAX_LENGTH
        ? normalized
        : normalized.substring(0, MAX_LENGTH) + "…";
  }
}
