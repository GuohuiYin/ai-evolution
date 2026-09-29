package com.aievolution.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aievolution.prompt.PromptLibrary;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * W11 #5：查询改写器——指代消解把追问改写为自足查询。
 *
 * <p>W15 #2（A1 裁决）契约变更：<b>首轮直通纪律废除</b>——语料指代（"这两家/谁"指语料实体而非 对话历史）证伪"首轮查询必自足"假设；首轮同样过改写模型，自足查询由
 * prompt 约定原样返回。
 */
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
    when(builder.defaultOptions(any())).thenReturn(builder);
    when(builder.build()).thenReturn(chatClient);
    this.builder = builder;
    promptLibrary = mock(PromptLibrary.class);
    when(promptLibrary.exists("query-rewrite-v1")).thenReturn(true);
    when(promptLibrary.get("query-rewrite-v1")).thenReturn("改写规则");
  }

  private ChatClient.Builder builder;

  private DeepSeekQueryRewriter rewriter() {
    return new DeepSeekQueryRewriter(builder, promptLibrary, "query-rewrite-v1", 0.0);
  }

  @Test
  void passThroughReturnsQueryUnchanged() {
    PassThroughQueryRewriter pass = new PassThroughQueryRewriter();
    assertThat(pass.rewrite("其中的 12987 是什么含义", List.of(new UserMessage("茅台工艺"))))
        .isEqualTo("其中的 12987 是什么含义");
  }

  @Test
  void firstTurnAlsoGoesThroughModel() {
    // 首轮不再直通：语料指代查询（"这两家"指语料中的茅台/宁德）首轮也不自足，
    // 必须过改写模型展开锚定词；无历史时上下文里要显式标注首轮
    when(callSpec.content()).thenReturn("贵州茅台和宁德时代谁的品牌壁垒更厚");

    String rewritten = rewriter().rewrite("这两家公司里谁的品牌壁垒更厚", List.of());

    assertThat(rewritten).isEqualTo("贵州茅台和宁德时代谁的品牌壁垒更厚");
    org.mockito.ArgumentCaptor<String> userCaptor =
        org.mockito.ArgumentCaptor.forClass(String.class);
    verify(spec).user(userCaptor.capture());
    assertThat(userCaptor.getValue()).contains("首轮").contains("这两家公司里谁的品牌壁垒更厚");
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
  void modelExceptionFallsBackToOriginal() {
    // A1 后改写是必经路径：模型不可用（402/超时）不得拖垮检索与对话，降级为原查询并留痕
    when(spec.user(anyString())).thenThrow(new RuntimeException("HTTP 402 Insufficient Balance"));

    assertThat(rewriter().rewrite("这两家公司谁的品牌壁垒更厚", List.of())).isEqualTo("这两家公司谁的品牌壁垒更厚");
  }

  @Test
  void rewriteTemperatureComesFromConfiguration() {
    // 改写是确定性任务（指代消解/格式变换，非创作）：温度策略外置 ai.rag.rewrite-temperature
    // （默认 0）——R3/R4 实测默认温度下同查询两轮措辞不同，检索排序漂移 ±2 条；
    // 本测试锁定"配置值被真实采信"而非硬编码（故意用非默认 0.7 注入）
    new DeepSeekQueryRewriter(builder, promptLibrary, "query-rewrite-v1", 0.7);

    @SuppressWarnings({"unchecked", "rawtypes"})
    org.mockito.ArgumentCaptor<ChatOptions.Builder> captor =
        org.mockito.ArgumentCaptor.forClass(ChatOptions.Builder.class);
    verify(builder).defaultOptions(captor.capture());
    assertThat(captor.getValue().build().getTemperature()).isEqualTo(0.7);
  }

  @Test
  void failsFastWhenPromptMissing() {
    when(promptLibrary.exists("nope")).thenReturn(false);
    assertThatThrownBy(() -> new DeepSeekQueryRewriter(builder, promptLibrary, "nope", 0.0))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("nope");
  }
}
