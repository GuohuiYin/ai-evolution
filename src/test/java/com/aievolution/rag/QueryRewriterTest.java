package com.aievolution.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aievolution.prompt.PromptLibrary;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;

/** W11 #5：查询改写器——指代消解把追问改写为自足查询；首轮直通零成本（不调模型）。 */
class QueryRewriterTest {

  private ChatClient.CallResponseSpec callSpec;
  private ChatClient.ChatClientRequestSpec spec;
  private PromptLibrary promptLibrary;

  @BeforeEach
  void setUp() {
    ChatClient chatClient = mock(ChatClient.class);
    spec = mock(ChatClient.ChatClientRequestSpec.class);
    callSpec = mock(ChatClient.CallResponseSpec.class);
    when(chatClient.prompt()).thenReturn(spec);
    when(spec.system(anyString())).thenReturn(spec);
    when(spec.user(anyString())).thenReturn(spec);
    when(spec.call()).thenReturn(callSpec);
    ChatClient.Builder builder = mock(ChatClient.Builder.class);
    when(builder.build()).thenReturn(chatClient);
    this.builder = builder;
    promptLibrary = mock(PromptLibrary.class);
    when(promptLibrary.exists("query-rewrite-v1")).thenReturn(true);
    when(promptLibrary.get("query-rewrite-v1")).thenReturn("改写规则");
  }

  private ChatClient.Builder builder;

  private DeepSeekQueryRewriter rewriter() {
    return new DeepSeekQueryRewriter(builder, promptLibrary, "query-rewrite-v1");
  }

  @Test
  void passThroughReturnsQueryUnchanged() {
    PassThroughQueryRewriter pass = new PassThroughQueryRewriter();
    assertThat(pass.rewrite("其中的 12987 是什么含义", List.of(new UserMessage("茅台工艺"))))
        .isEqualTo("其中的 12987 是什么含义");
  }

  @Test
  void firstTurnSkipsModelCall() {
    // 首轮零成本直通：无历史时原样返回，一次模型调用都不发
    assertThat(rewriter().rewrite("茅台的酿造工艺", List.of())).isEqualTo("茅台的酿造工艺");
    verifyNoInteractions(spec);
  }

  @Test
  void rewritesFollowUpWithHistory() {
    when(callSpec.content()).thenReturn("茅台 12987 工艺的含义");

    String rewritten =
        rewriter()
            .rewrite(
                "其中的 12987 是什么含义",
                List.of(new UserMessage("茅台的酿造工艺是什么"), new AssistantMessage("茅台采用 12987 工艺……")));

    assertThat(rewritten).isEqualTo("茅台 12987 工艺的含义");
    // 历史与追问都要进改写上下文
    org.mockito.ArgumentCaptor<String> userCaptor =
        org.mockito.ArgumentCaptor.forClass(String.class);
    verify(spec).user(userCaptor.capture());
    assertThat(userCaptor.getValue()).contains("茅台的酿造工艺是什么").contains("其中的 12987 是什么含义");
  }

  @Test
  void blankModelOutputFallsBackToOriginal() {
    // 改写失败的兜底：用原始追问检索，宁可指代未消解也不拿空串去向量化
    when(callSpec.content()).thenReturn("  ");

    assertThat(rewriter().rewrite("它呢？", List.of(new UserMessage("茅台工艺")))).isEqualTo("它呢？");
  }

  @Test
  void failsFastWhenPromptMissing() {
    when(promptLibrary.exists("nope")).thenReturn(false);
    assertThatThrownBy(() -> new DeepSeekQueryRewriter(builder, promptLibrary, "nope"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("nope");
  }
}
