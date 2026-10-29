package com.aievolution.tool;

import com.aievolution.infra.LogSummaries;
import com.aievolution.infra.TraceSpans;
import java.util.Arrays;
import java.util.Map;
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
 * <p>W25 #1a（ADR-0020）：同一切面兼作工具段链路埋点——每次调用产出 {@code tool.<名>} span， 按 OTel GenAI 语义挂 {@code
 * gen_ai.operation.name=execute_tool} / {@code gen_ai.tool.name}， 失败路径身份属性同样在场（执行前打标），异常经 {@link
 * TraceSpans} 记 span error。
 *
 * <p>摘要策略引用 {@link LogSummaries} 单点定义（A11 两次即收口），不各自持有。
 */
@Aspect
@Component
public class ToolAuditAspect {

  private static final Logger auditLog = LoggerFactory.getLogger("tool-audit");

  private final TraceSpans spans;

  public ToolAuditAspect(TraceSpans spans) {
    this.spans = spans;
  }

  @Around("@annotation(org.springframework.ai.tool.annotation.Tool)")
  public Object around(ProceedingJoinPoint pjp) throws Throwable {
    String tool = pjp.getSignature().getName();
    String args =
        Arrays.stream(pjp.getArgs()).map(String::valueOf).collect(Collectors.joining(","));
    // 审计计时用 nanoTime：currentTimeMillis 受系统时钟回拨影响，耗时审计必须单调
    long startNanos = System.nanoTime();
    return spans.traced(
        "tool." + tool,
        Map.of(
            "gen_ai.operation.name",
            "execute_tool",
            "gen_ai.tool.name",
            tool,
            "openinference.span.kind",
            "TOOL",
            "input.value",
            args),
        () -> {
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
        },
        (span, result) -> span.tag("output.value", LogSummaries.summarize(result)));
  }

  private long elapsedMillis(long startNanos) {
    return (System.nanoTime() - startNanos) / 1_000_000;
  }
}
