package com.aievolution.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aievolution.cost.CostChannel;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatOptions;

/** 模型分档测试（W25 #3）：切档只改 model 字段（温度等调参不丢）、空档零行为变化。 */
class ModelTierAdvisorTest {

  private static final ChatClientResponse OK_RESPONSE =
      new ChatClientResponse(new ChatResponse(List.of()), Map.of());

  private final CallAdvisorChain chain = mock(CallAdvisorChain.class);

  private ModelTierAdvisor advisorWithAgentTier() {
    // rag 留空档（不切），agent 升 pro——覆盖「按通路独立定档」
    return new ModelTierAdvisor(
        Map.of(
            CostChannel.RAG, "",
            CostChannel.AGENT, "deepseek-v4-pro",
            CostChannel.PIPELINE, "",
            CostChannel.ANALYSIS, ""));
  }

  @Test
  void switchesModelForConfiguredChannel() {
    when(chain.nextCall(any())).thenReturn(OK_RESPONSE);
    ChatClientRequest request =
        new ChatClientRequest(
            new Prompt("茅台年报", DeepSeekChatOptions.builder().model("deepseek-v4-flash").build()),
            Map.of(CostChannel.CONTEXT_KEY, "agent"));

    advisorWithAgentTier().adviseCall(request, chain);

    ArgumentCaptor<ChatClientRequest> captor = ArgumentCaptor.forClass(ChatClientRequest.class);
    verify(chain).nextCall(captor.capture());
    assertThat(captor.getValue().prompt().getOptions().getModel()).isEqualTo("deepseek-v4-pro");
  }

  @Test
  void preservesExistingOptionsWhenSwitching() {
    // 改写器的温度调参是前车之鉴：切档必须只动 model 字段
    when(chain.nextCall(any())).thenReturn(OK_RESPONSE);
    ChatClientRequest request =
        new ChatClientRequest(
            new Prompt(
                "q",
                DeepSeekChatOptions.builder().model("deepseek-v4-flash").temperature(0.0).build()),
            Map.of(CostChannel.CONTEXT_KEY, "agent"));

    advisorWithAgentTier().adviseCall(request, chain);

    ArgumentCaptor<ChatClientRequest> captor = ArgumentCaptor.forClass(ChatClientRequest.class);
    verify(chain).nextCall(captor.capture());
    assertThat(captor.getValue().prompt().getOptions().getModel()).isEqualTo("deepseek-v4-pro");
    assertThat(captor.getValue().prompt().getOptions().getTemperature()).isEqualTo(0.0);
  }

  @Test
  void blankTierPassesThroughUntouched() {
    // 空档 = 不切（默认零行为变化）：请求对象原样透传
    when(chain.nextCall(any())).thenReturn(OK_RESPONSE);
    ChatClientRequest request =
        new ChatClientRequest(new Prompt("q"), Map.of(CostChannel.CONTEXT_KEY, "rag"));

    advisorWithAgentTier().adviseCall(request, chain);

    ArgumentCaptor<ChatClientRequest> captor = ArgumentCaptor.forClass(ChatClientRequest.class);
    verify(chain).nextCall(captor.capture());
    assertThat(captor.getValue()).isSameAs(request);
  }

  @Test
  void unmarkedChannelPassesThroughUntouched() {
    // eval judge 等裸客户端无通路标记：不切档
    when(chain.nextCall(any())).thenReturn(OK_RESPONSE);
    ChatClientRequest request = new ChatClientRequest(new Prompt("q"), Map.of());

    advisorWithAgentTier().adviseCall(request, chain);

    ArgumentCaptor<ChatClientRequest> captor = ArgumentCaptor.forClass(ChatClientRequest.class);
    verify(chain).nextCall(captor.capture());
    assertThat(captor.getValue()).isSameAs(request);
  }
}
