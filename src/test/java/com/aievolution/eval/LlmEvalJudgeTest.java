package com.aievolution.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aievolution.prompt.PromptLibrary;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

class LlmEvalJudgeTest {

  private ChatModel fakeChatModelReturning(String text) {
    ChatModel chatModel = mock(ChatModel.class);
    when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
    when(chatModel.call(any(Prompt.class)))
        .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(text)))));
    return chatModel;
  }

  private PromptLibrary stubLibrary() {
    PromptLibrary library = mock(PromptLibrary.class);
    when(library.get("eval-judge-v1"))
        .thenReturn("问题：{query}\n期望：{expectedBehavior}\n参照：{referenceAnswer}\n回答：{answer}");
    return library;
  }

  private GenerationCase sampleCase() {
    return new GenerationCase("问题", "factual", "answer-with-source", "参照");
  }

  @Test
  void parsesWellFormedJudgeJson() {
    ChatModel model =
        fakeChatModelReturning(
            "{\"accuracy\":2,\"compliance\":1,\"grounding\":2,\"reason\":\"缺时点\"}");
    LlmEvalJudge judge = new LlmEvalJudge(model, stubLibrary());

    EvalJudge.JudgeScore score = judge.judge(sampleCase(), "被测回答");

    assertThat(score.accuracy()).isEqualTo(2);
    assertThat(score.compliance()).isEqualTo(1);
    assertThat(score.grounding()).isEqualTo(2);
    assertThat(score.reason()).isEqualTo("缺时点");
  }

  @Test
  void toleratesMarkdownFencedJson() {
    // 模型常见习惯：JSON 包在 ```json 围栏里，judge 解析必须容忍
    ChatModel model =
        fakeChatModelReturning(
            "```json\n{\"accuracy\":1,\"compliance\":2,\"grounding\":2,\"reason\":\"部分遗漏\"}\n```");
    LlmEvalJudge judge = new LlmEvalJudge(model, stubLibrary());

    assertThat(judge.judge(sampleCase(), "回答").accuracy()).isEqualTo(1);
  }

  @Test
  void failsFastOnUnparseableJudgeOutput() {
    ChatModel model = fakeChatModelReturning("我觉得这个回答还行");
    LlmEvalJudge judge = new LlmEvalJudge(model, stubLibrary());

    // judge 输出不可解析是评估事故，必须 fail-fast 而非兜底打分（同 A 系约定）
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalStateException.class, () -> judge.judge(sampleCase(), "回答"));
  }

  // ── W24 #2 检索信号输入侧注入 ──

  private PromptLibrary signalLibrary() {
    PromptLibrary library = mock(PromptLibrary.class);
    when(library.get("eval-judge-v1")).thenReturn("信号：{retrievalSignal}\n回答：{answer}");
    return library;
  }

  @Test
  void emptyRetrievalSignalRenderedIntoPrompt() {
    ChatModel model =
        fakeChatModelReturning(
            "{\"accuracy\":0,\"compliance\":2,\"grounding\":2,\"reason\":\"设计性拒答\"}");
    LlmEvalJudge judge = new LlmEvalJudge(model, signalLibrary());

    judge.judge(sampleCase(), "知识库中未找到与问题相关的资料。", true);

    org.mockito.ArgumentCaptor<Prompt> captor = org.mockito.ArgumentCaptor.forClass(Prompt.class);
    org.mockito.Mockito.verify(model).call(captor.capture());
    assertThat(captor.getValue().getContents()).contains("empty-retrieval");
  }

  @Test
  void normalAnswerSignalIsNone() {
    ChatModel model =
        fakeChatModelReturning(
            "{\"accuracy\":2,\"compliance\":2,\"grounding\":2,\"reason\":\"满分\"}");
    LlmEvalJudge judge = new LlmEvalJudge(model, signalLibrary());

    judge.judge(sampleCase(), "正常回答", false);

    org.mockito.ArgumentCaptor<Prompt> captor = org.mockito.ArgumentCaptor.forClass(Prompt.class);
    org.mockito.Mockito.verify(model).call(captor.capture());
    assertThat(captor.getValue().getContents()).contains("信号：none");
  }
}
