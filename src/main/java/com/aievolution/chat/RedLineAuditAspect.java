package com.aievolution.chat;

import com.aievolution.compliance.RedLineGuard;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 红线告警切面（W7-Step1 P1）：所有 {@link ChatService} 实现返回的回答统一过 {@link RedLineGuard}。
 *
 * <p>与 {@code ToolAuditAspect} 同范式（ADR-0008）：AOP 无入侵，新增 ChatService 实现自动获得检测。 只告警（WARN 留痕，traceId
 * 自动串链）不拦截——先积累真实命中数据再议硬拦（ADR-0011）。
 */
@Aspect
@Component
public class RedLineAuditAspect {

  private static final Logger log = LoggerFactory.getLogger(RedLineAuditAspect.class);

  private final RedLineGuard redLineGuard;

  public RedLineAuditAspect(RedLineGuard redLineGuard) {
    this.redLineGuard = redLineGuard;
  }

  @Around("execution(* com.aievolution.chat.ChatService.chat(..))")
  public Object scanReplyForRedLine(ProceedingJoinPoint pjp) throws Throwable {
    Object result = pjp.proceed();
    if (result instanceof ChatAnswer answer) {
      List<String> hits = redLineGuard.scan(answer.reply());
      if (!hits.isEmpty()) {
        log.warn("stage=REDLINE_HIT patterns={} excerpt={}", hits, abbreviate(answer.reply()));
      }
    }
    return result;
  }

  private static String abbreviate(String text) {
    String oneLine = text.replaceAll("\\s+", " ").trim();
    return oneLine.length() <= 50 ? oneLine : oneLine.substring(0, 50) + "…";
  }
}
