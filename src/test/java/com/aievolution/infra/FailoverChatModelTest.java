package com.aievolution.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.web.client.ResourceAccessException;
import reactor.core.publisher.Flux;

/**
 * W12 #4：模型自动降级装饰器行为锁定——超时/5xx 切备用（Qwen），4xx 不切（请求自身问题， 换供应商同样失败）；流式只在"尚未吐出任何
 * token"时允许切换，中途失败不重放防内容重复。
 */
class FailoverChatModelTest {

  private static final Prompt PROMPT = new Prompt("茅台营收");

  private ChatModel primary;
  private ChatModel fallback;
  private ChatOptions fallbackOptions;
  private SimpleMeterRegistry meterRegistry;
  private FailoverChatModel model;

  @BeforeEach
  void setUp() {
    primary = mock(ChatModel.class);
    fallback = mock(ChatModel.class);
    // 降级路径用备用模型的默认 options 重建 Prompt（跨供应商不可复用主力 options）
    fallbackOptions = mock(ChatOptions.class);
    when(fallback.getDefaultOptions()).thenReturn(fallbackOptions);
    meterRegistry = new SimpleMeterRegistry();
    model = new FailoverChatModel(primary, fallback, meterRegistry, "qwen-fallback");
  }

  private static ChatResponse responseOf(String text) {
    return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
  }

  @Test
  void primarySuccessNeverTouchesFallback() {
    when(primary.call(any(Prompt.class))).thenReturn(responseOf("主力回答"));

    assertThat(model.call(PROMPT).getResult().getOutput().getText()).isEqualTo("主力回答");
    verifyNoInteractions(fallback);
    assertThat(failoverCount()).isZero();
  }

  private double failoverCount() {
    // 计数器懒注册（首次降级才创建）：缺席即零次
    io.micrometer.core.instrument.Counter counter =
        meterRegistry.find("model.failover.total").tags("fallback", "qwen-fallback").counter();
    return counter == null ? 0 : counter.count();
  }

  @Test
  void transient5xxTriggersFallback() {
    when(primary.call(any(Prompt.class)))
        .thenThrow(new TransientAiException("503 Service Unavailable"));
    when(fallback.call(any(Prompt.class))).thenReturn(responseOf("备用回答"));

    assertThat(model.call(PROMPT).getResult().getOutput().getText()).isEqualTo("备用回答");
    // 降级 prompt 的 options 必须是备用模型自己的默认选项（DeepSeekChatOptions 会被
    // OpenAiChatModel.createRequest 的强转拒绝，反之亦然——W12 #4c 实测抓获）
    verify(fallback).call(argThat((Prompt p) -> p.getOptions() == fallbackOptions));
    assertThat(failoverCount()).isEqualTo(1.0);
  }

  @Test
  void defaultOptionsDelegateToPrimary() {
    // ChatClient 装配从 getOptions()/getDefaultOptions() 取默认选项；接口默认实现返回
    // 通用 DefaultChatOptions，DeepSeekChatModel.createRequest 强转即炸——必须透传主力同型
    ChatOptions primaryOptions = mock(ChatOptions.class);
    when(primary.getOptions()).thenReturn(primaryOptions);
    when(primary.getDefaultOptions()).thenReturn(primaryOptions);

    assertThat(model.getOptions()).isSameAs(primaryOptions);
    assertThat(model.getDefaultOptions()).isSameAs(primaryOptions);
  }

  @Test
  void timeoutTriggersFallback() {
    when(primary.call(any(Prompt.class))).thenThrow(new ResourceAccessException("Read timed out"));
    when(fallback.call(any(Prompt.class))).thenReturn(responseOf("备用回答"));

    assertThat(model.call(PROMPT).getResult().getOutput().getText()).isEqualTo("备用回答");
  }

  @Test
  void nonTransient4xxPropagatesWithoutFallback() {
    // 402 余额不足/400 参数错误属请求或账户语义问题，切换无意义——原样抛出
    when(primary.call(any(Prompt.class)))
        .thenThrow(new NonTransientAiException("402 Insufficient Balance"));

    assertThatThrownBy(() -> model.call(PROMPT)).isInstanceOf(NonTransientAiException.class);
    verifyNoInteractions(fallback);
  }

  @Test
  void fallbackFailurePropagates() {
    when(primary.call(any(Prompt.class))).thenThrow(new TransientAiException("503"));
    when(fallback.call(any(Prompt.class))).thenThrow(new TransientAiException("备用也挂了"));

    assertThatThrownBy(() -> model.call(PROMPT))
        .isInstanceOf(TransientAiException.class)
        .hasMessageContaining("备用也挂了");
  }

  @Test
  void wrappedTransientCauseStillTriggersFallback() {
    // 响应式链路常见包装：ReactiveException 包 TransientAiException，沿 cause 链识别
    when(primary.call(any(Prompt.class)))
        .thenThrow(
            new RuntimeException("reactor wrapper", new TransientAiException("503 upstream")));
    when(fallback.call(any(Prompt.class))).thenReturn(responseOf("备用回答"));

    assertThat(model.call(PROMPT).getResult().getOutput().getText()).isEqualTo("备用回答");
  }

  @Test
  void streamFailoverAllowedBeforeFirstEmission() {
    when(primary.stream(any(Prompt.class))).thenReturn(Flux.error(new TransientAiException("503")));
    when(fallback.stream(any(Prompt.class))).thenReturn(Flux.just(responseOf("备用流式块")));

    List<String> chunks =
        model.stream(PROMPT).map(r -> r.getResult().getOutput().getText()).collectList().block();

    assertThat(chunks).containsExactly("备用流式块");
  }

  @Test
  void streamMidFlightFailurePropagatesWithoutRestart() {
    // 已吐 token 后失败：切换会重放整段回答造成重复内容，宁可把错误交给 SSE 客户端
    when(primary.stream(any(Prompt.class)))
        .thenReturn(
            Flux.concat(
                Flux.just(responseOf("前半段")),
                Flux.error(new ResourceAccessException("connection reset"))));

    assertThatThrownBy(() -> model.stream(PROMPT).collectList().block())
        .isInstanceOf(ResourceAccessException.class);
    verifyNoInteractions(fallback);
  }
}
