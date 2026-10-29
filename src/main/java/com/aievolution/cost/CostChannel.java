package com.aievolution.cost;

/**
 * 通路标记（W25 #2a-ii）：一次模型调用属于哪条业务通路。 各服务在构建 ChatClient 时经 advisor 参数写入请求上下文 （{@code defaultAdvisors(a
 * -> a.param(CONTEXT_KEY, ...))}），{@link com.aievolution.infra.TokenUsageAdvisor}
 * 在记账时读取——常量单点定义（A12）， 新增通路在此加常量，调用方与记账方同步获得。
 */
public final class CostChannel {

  /** ChatClient 请求上下文键：通路标记经 advisor 参数传递 */
  public static final String CONTEXT_KEY = "cost.channel";

  public static final String RAG = "rag";
  public static final String AGENT = "agent";
  public static final String PIPELINE = "pipeline";
  public static final String ANALYSIS = "analysis";

  /** 未标记通路（eval judge 等裸客户端、历史数据）的兜底桶名 */
  public static final String UNKNOWN = "unknown";

  private CostChannel() {}
}
