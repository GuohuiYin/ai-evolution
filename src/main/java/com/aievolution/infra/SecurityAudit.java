package com.aievolution.infra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 安全事件审计单点（W31 #4）：401/403/429/审批决定等安全事件统一为结构化事件， 走独立 logger「{@value #LOGGER_NAME}」与业务日志分流（logback
 * 路由独立滚动文件 logs/security-audit.log， 值守窗口可直接对照；控制台同时保留可见）。
 *
 * <p>事件格式：{@code event=<类型> k=v ...}——k=v 形态便于 grep 与后续采集管线消费； traceId 由 MDC 随日志 pattern
 * 自动带出，不落事件体。
 *
 * <p>纪律（A12 单点化）：新安全事件类型一律经本类发出，业务代码不各自 logger.warn—— 事件命名与字段口径全项目一致，审计时多处不一致即缺陷。
 */
public final class SecurityAudit {

  public static final String LOGGER_NAME = "security-audit";

  private static final Logger AUDIT = LoggerFactory.getLogger(LOGGER_NAME);

  private SecurityAudit() {}

  /** 401：无凭证或 JWT 非法/过期（认证失败） */
  public static void authFailure(String path, String clientIp) {
    AUDIT.info("event=AUTH_FAILURE path={} client={}", path, clientIp);
  }

  /** 403：凭证合法但角色不足（授权失败） */
  public static void authzDenied(String path, String subject) {
    AUDIT.info("event=AUTHZ_DENIED path={} subject={}", path, subject);
  }

  /** 429：限流命中（identity=限流分桶键，sub: 或 ip: 前缀） */
  public static void rateLimited(String identity, String path) {
    AUDIT.info("event=RATE_LIMITED identity={} path={}", identity, path);
  }

  /** HITL 人工决定（approve=放行背书 / reject=驳回） */
  public static void hitlDecision(String workflowId, String decision, String comment) {
    AUDIT.info(
        "event=HITL_DECISION workflow={} decision={} comment={}",
        workflowId,
        decision,
        abbreviate(comment));
  }

  private static String abbreviate(String text) {
    if (text == null || text.isBlank()) {
      return "-";
    }
    String oneLine = text.replaceAll("\\s+", " ").trim();
    return oneLine.length() <= 80 ? oneLine : oneLine.substring(0, 80) + "…";
  }
}
