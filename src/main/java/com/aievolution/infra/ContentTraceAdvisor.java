package com.aievolution.infra;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;

/**
 * 消息内容进链路 span（W25 #1a 收尾，Owner 裁决 2026-10-29 默认开）：把本轮 prompt 与回复全文 作为 {@code input.value}/{@code
 * output.value} 打在当前 advisor 段上——这两个键经 #1c 实测是 Phoenix/Langfuse 双面板通吃的内容映射位，点开即可读消息体，排障不再回日志捞。
 *
 * <p>背景：Spring AI 2.0.1 的库级开关（{@code spring.ai.chat.observations.log-prompt}）已不再产出
 * 内容属性（上游以隐私为由移除，开启只打一条告警日志），故按既有 advisor 挂载模式自研。
 *
 * <p>安全前提：默认开仅因 OTLP 端点是本地 Phoenix 不出本机；接外部观测后端必须经 {@code ai.chat.content-tracing.enabled=false}
 * 关闭（消息内容即隐私面/攻击面）。 巨型 prompt（检索上下文拼接）按 {@code ai.chat.content-tracing.max-chars} 截断防撑爆 span。
 * Tracer 缺席直执——与 {@link TraceSpans} 同款约定：埋点永不改变业务语义。
 */
public class ContentTraceAdvisor implements CallAdvisor {

  private final ObjectProvider<Tracer> tracer;
  private final int maxChars;

  public ContentTraceAdvisor(ObjectProvider<Tracer> tracer, int maxChars) {
    this.tracer = tracer;
    this.maxChars = maxChars;
  }

  @Override
  public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
    // 入参在调用前快照：模型响应回来后再取 request 可能已被下游 advisor 改写
    String input = truncate(request.prompt().getContents());
    ChatClientResponse response = chain.nextCall(request);
    Tracer t = tracer.getIfAvailable();
    Span span = t == null ? null : t.currentSpan();
    if (span != null) {
      span.tag("input.value", input);
      span.tag("output.value", truncate(extractText(response)));
    }
    return response;
  }

  private static String extractText(ChatClientResponse response) {
    ChatResponse chatResponse = response.chatResponse();
    if (chatResponse == null || chatResponse.getResult() == null) {
      return "";
    }
    return chatResponse.getResult().getOutput().getText();
  }

  private String truncate(String text) {
    if (text == null || text.length() <= maxChars) {
      return text == null ? "" : text;
    }
    return text.substring(0, maxChars) + "…[截断]";
  }

  @Override
  public String getName() {
    return "ContentTraceAdvisor";
  }

  @Override
  public int getOrder() {
    // 尽量靠近链尾（TokenUsageAdvisor 同款考量）：拿到的是最终发往模型的请求与真实响应
    return Ordered.LOWEST_PRECEDENCE - 2;
  }
}
