package com.aievolution.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.beans.factory.ObjectProvider;

/** {@link ContentTraceAdvisor} 单测（W25 #1a 收尾）：内容打标 / 截断 / Tracer 缺席直执三条。 */
class ContentTraceAdvisorTest {

  private static ChatClientRequest request(String text) {
    return new ChatClientRequest(new org.springframework.ai.chat.prompt.Prompt(text), Map.of());
  }

  private static ChatClientResponse response(String text) {
    return new ChatClientResponse(
        new ChatResponse(List.of(new Generation(new AssistantMessage(text)))), Map.of());
  }

  @SuppressWarnings("unchecked")
  private static ObjectProvider<Tracer> providerOf(Tracer tracer) {
    ObjectProvider<Tracer> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(tracer);
    return provider;
  }

  private static CallAdvisorChain chainReturning(ChatClientResponse response) {
    CallAdvisorChain chain = mock(CallAdvisorChain.class);
    when(chain.nextCall(any())).thenReturn(response);
    return chain;
  }

  @Test
  void tagsCurrentSpanWithInputAndOutput() {
    Tracer tracer = mock(Tracer.class);
    Span span = mock(Span.class);
    when(tracer.currentSpan()).thenReturn(span);

    ChatClientResponse out =
        new ContentTraceAdvisor(providerOf(tracer), 4000)
            .adviseCall(request("茅台分红多少"), chainReturning(response("每10股派239.57元")));

    assertThat(out.chatResponse().getResult().getOutput().getText()).isEqualTo("每10股派239.57元");
    verify(span).tag("input.value", "茅台分红多少");
    verify(span).tag("output.value", "每10股派239.57元");
  }

  @Test
  void truncatesOversizedContent() {
    Tracer tracer = mock(Tracer.class);
    Span span = mock(Span.class);
    when(tracer.currentSpan()).thenReturn(span);
    String longText = "长".repeat(5000);

    new ContentTraceAdvisor(providerOf(tracer), 100)
        .adviseCall(request(longText), chainReturning(response(longText)));

    verify(span).tag(eq("input.value"), eq("长".repeat(100) + "…[截断]"));
    verify(span).tag(eq("output.value"), eq("长".repeat(100) + "…[截断]"));
  }

  @Test
  void passesThroughWhenTracerAbsent() {
    ObjectProvider<Tracer> empty = providerOf(null);

    ChatClientResponse out =
        new ContentTraceAdvisor(empty, 4000)
            .adviseCall(request("问题"), chainReturning(response("答案")));

    assertThat(out.chatResponse().getResult().getOutput().getText()).isEqualTo("答案");
  }

  @Test
  void skipsTaggingWhenNoCurrentSpan() {
    Tracer tracer = mock(Tracer.class);
    when(tracer.currentSpan()).thenReturn(null);

    ChatClientResponse out =
        new ContentTraceAdvisor(providerOf(tracer), 4000)
            .adviseCall(request("问题"), chainReturning(response("答案")));

    assertThat(out.chatResponse().getResult().getOutput().getText()).isEqualTo("答案");
    verify(tracer, never()).nextSpan();
  }
}
