package com.aievolution.rag;

import com.aievolution.infra.TraceSpans;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 检索段链路埋点（W25 #1a，ADR-0020）：每次知识检索产出一个 {@code rag.retrieval} span， 挂 RETRIEVER 类型（Phoenix
 * 按检索段原生渲染）+ 查询原文 + 命中数/dense 分数标尺。
 *
 * <p>切面而非侵入 {@link VectorStoreKnowledgeRetriever}：观测是横切关注点（ToolAuditAspect
 * 先例），领域类的三个构造器与全部测试零改动。切入点声明在接口方法上——外部调用经代理 触发一次；实现内部自调（retrieve→retrieveWithEvidence）不过代理，不会双开
 * span。
 */
@Aspect
@Component
public class RetrievalTraceAspect {

  private final TraceSpans spans;

  public RetrievalTraceAspect(TraceSpans spans) {
    this.spans = spans;
  }

  @Around(
      "execution(* com.aievolution.rag.KnowledgeRetriever+.retrieve(..))"
          + " || execution(* com.aievolution.rag.KnowledgeRetriever+.retrieveWithEvidence(..))")
  public Object traceRetrieval(ProceedingJoinPoint pjp) throws Throwable {
    String query = pjp.getArgs().length > 0 ? String.valueOf(pjp.getArgs()[0]) : "";
    return spans.traced(
        "rag.retrieval",
        pjp::proceed,
        (span, result) -> {
          span.tag("openinference.span.kind", "RETRIEVER");
          span.tag("input.value", query);
          if (result instanceof RetrievalEvidence ev) {
            span.tag("rag.hits", String.valueOf(ev.hits().size()));
            if (ev.denseTopScore() != null) {
              span.tag("rag.dense_top_score", String.format("%.3f", ev.denseTopScore()));
            }
          } else if (result instanceof List<?> hits) {
            span.tag("rag.hits", String.valueOf(hits.size()));
          }
        });
  }
}
