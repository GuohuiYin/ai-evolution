package com.aievolution.tool;

import com.aievolution.infra.LogSummaries;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 工具调用审计切面：所有 {@code @Tool} 方法的调用参数、结果摘要、耗时、成败统一留痕。
 *
 * <p>金融合规刚需（Harness Verify 层）：模型触发的每一次数据访问都要可回放。 雏形落地为专用 logger（{@code
 * tool-audit}），生产可无缝切换到审计表/ES——切面无入侵。
 *
 * <p>摘要策略引用 {@link LogSummaries} 单点定义（A11 两次即收口），不各自持有。
 */
@Aspect
@Component
public class ToolAuditAspect {

  private static final Logger auditLog = LoggerFactory.getLogger("tool-audit");

  @Around("@annotation(org.springframework.ai.tool.annotation.Tool)")
  public Object around(ProceedingJoinPoint pjp) throws Throwable {
    String tool = pjp.getSignature().getName();
    String args =
        Arrays.stream(pjp.getArgs()).map(String::valueOf).collect(Collectors.joining(","));
    // 审计计时用 nanoTime：currentTimeMillis 受系统时钟回拨影响，耗时审计必须单调
    long startNanos = System.nanoTime();
    try {
      Object result = pjp.proceed();
      auditLog.info(
          "tool={} args=[{}] outcome=success elapsedMs={} resultSummary={}",
          tool,
          args,
          elapsedMillis(startNanos),
          LogSummaries.summarize(result));
      return result;
    } catch (Throwable e) {
      auditLog.warn(
          "tool={} args=[{}] outcome=error elapsedMs={} errorType={}",
          tool,
          args,
          elapsedMillis(startNanos),
          e.getClass().getSimpleName());
      throw e;
    }
  }

  private long elapsedMillis(long startNanos) {
    return (System.nanoTime() - startNanos) / 1_000_000;
  }
}
