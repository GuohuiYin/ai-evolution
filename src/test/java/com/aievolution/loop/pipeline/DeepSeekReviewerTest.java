package com.aievolution.loop.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aievolution.prompt.PromptLibrary;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;

/** W22 #1c：Reviewer 组件——独立于作者的语义复核（裁判不进赛场）。 输出不可解析时 fail-closed 打回（宁可不答不可错答，设计 §1.3）。 */
class DeepSeekReviewerTest {

  private ChatClient.ChatClientRequestSpec spec;
  private ChatClient.CallResponseSpec callSpec;
  private PromptLibrary promptLibrary;
  private SimpleMeterRegistry meterRegistry;
  private ChatClient.Builder builder;

  @BeforeEach
  void setUp() {
    ChatClient chatClient = mock(ChatClient.class);
    spec = mock(ChatClient.ChatClientRequestSpec.class);
    callSpec = mock(ChatClient.CallResponseSpec.class);
    when(chatClient.prompt()).thenReturn(spec);
    when(spec.user(anyString())).thenReturn(spec);
    when(spec.call()).thenReturn(callSpec);
    builder = mock(ChatClient.Builder.class);
    when(builder.build()).thenReturn(chatClient);
    meterRegistry = new SimpleMeterRegistry();
    promptLibrary = mock(PromptLibrary.class);
    when(promptLibrary.exists("reviewer-v1")).thenReturn(true);
    when(promptLibrary.get("reviewer-v1"))
        .thenReturn("复核规则\n【用户问题】\n{query}\n【证据包】\n{evidence}\n【草稿终答】\n{draft}");
  }

  private DeepSeekReviewer reviewer() {
    return new DeepSeekReviewer(builder, promptLibrary, "reviewer-v1", meterRegistry);
  }

  private EvidencePack sampleEvidence() {
    return new EvidencePack(
        List.of(
            new EvidencePack.EvidenceItem("rag-search", "{\"q\":\"分红\"}", "茅台 2024 年度分红比例 75%")));
  }

  @Test
  void failsFastWhenPromptMissing() {
    when(promptLibrary.exists("nope")).thenReturn(false);
    assertThatThrownBy(() -> new DeepSeekReviewer(builder, promptLibrary, "nope", meterRegistry))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("nope");
  }

  @Test
  void passVerdictParsed() {
    when(callSpec.content()).thenReturn("{\"verdict\":\"PASS\",\"comment\":\"\"}");

    ReviewVerdict verdict =
        reviewer().review("分红多少", sampleEvidence(), new DraftAnswer("分红比例 75%"));

    assertThat(verdict.isPass()).isTrue();
    assertThat(meterRegistry.find("reviewer.parse.fallback.total").counter()).isNull();
  }

  @Test
  void rejectVerdictWithCommentParsed() {
    when(callSpec.content()).thenReturn("{\"verdict\":\"REJECT\",\"comment\":\"净利数字在证据包中无出处\"}");

    ReviewVerdict verdict =
        reviewer().review("净利多少", sampleEvidence(), new DraftAnswer("净利 857 亿"));

    assertThat(verdict.isPass()).isFalse();
    assertThat(verdict.comment()).isEqualTo("净利数字在证据包中无出处");
  }

  @Test
  void toleratesWhitespaceFenceAndLowercaseVerdict() {
    when(callSpec.content()).thenReturn("\n```json\n{\"verdict\":\"pass\"}\n```");

    ReviewVerdict verdict = reviewer().review("问题", sampleEvidence(), new DraftAnswer("草稿"));

    assertThat(verdict.isPass()).isTrue();
  }

  @Test
  void questionEvidenceAndDraftRenderedIntoUserMessage() {
    when(callSpec.content()).thenReturn("{\"verdict\":\"PASS\",\"comment\":\"\"}");

    reviewer().review("分红多少", sampleEvidence(), new DraftAnswer("分红比例 75%"));

    ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
    verify(spec).user(userCaptor.capture());
    // 复核三料齐备：问题 + 证据（工具/入参/观测全留，出处可核对）+ 草稿
    assertThat(userCaptor.getValue())
        .contains("分红多少")
        .contains("rag-search")
        .contains("{\"q\":\"分红\"}")
        .contains("茅台 2024 年度分红比例 75%")
        .contains("分红比例 75%")
        .doesNotContain("{query}")
        .doesNotContain("{evidence}")
        .doesNotContain("{draft}");
  }

  @Test
  void unparseableOutputFailsClosed() {
    // 复核输出不可解析 → 打回而非放行（fail-closed：宁可打回重来，不可放过未复核草稿）
    when(callSpec.content()).thenReturn("我觉得还行吧");

    ReviewVerdict verdict = reviewer().review("问题", sampleEvidence(), new DraftAnswer("草稿"));

    assertThat(verdict.isPass()).isFalse();
    assertThat(verdict.comment()).contains("不可解析");
    assertThat(meterRegistry.get("reviewer.parse.fallback.total").counter().count()).isEqualTo(1.0);
  }

  @Test
  void unknownVerdictValueFailsClosed() {
    when(callSpec.content()).thenReturn("{\"verdict\":\"MAYBE\",\"comment\":\"\"}");

    ReviewVerdict verdict = reviewer().review("问题", sampleEvidence(), new DraftAnswer("草稿"));

    assertThat(verdict.isPass()).isFalse();
    assertThat(meterRegistry.get("reviewer.parse.fallback.total").counter().count()).isEqualTo(1.0);
  }
}
