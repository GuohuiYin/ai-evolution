package com.aievolution.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.aievolution.chat.ChatAnswer;
import com.aievolution.chat.ChatService;
import java.util.List;
import org.junit.jupiter.api.Test;

class GenerationEvaluatorTest {

  private GenerationCase sampleCase() {
    return new GenerationCase("测试问题", "factual", "answer-with-source", "参照答案");
  }

  @Test
  void evaluatesAnswerThroughProductionEntryThenJudges() {
    // 评的就是用的：回答必须来自生产统一入口（ChatService），不是旁路
    ChatService chatService =
        (message, conversationId) -> new ChatAnswer("被测回答：" + message, List.of());
    EvalJudge judge = (c, answer) -> new EvalJudge.JudgeScore(2, 2, 1, "理由");
    GenerationEvaluator evaluator = new GenerationEvaluator(chatService, judge);

    List<GenerationEvaluator.GenEvalResult> results = evaluator.evaluate(List.of(sampleCase()));

    assertThat(results).hasSize(1);
    GenerationEvaluator.GenEvalResult r = results.getFirst();
    assertThat(r.answer()).isEqualTo("被测回答：测试问题");
    assertThat(r.score().accuracy()).isEqualTo(2);
    assertThat(r.score().total()).isEqualTo(5);
  }

  @Test
  void multiTurnCaseRunsScriptInSameConversationAndJudgesLastAnswer() {
    // W11 #6：多轮用例按脚本顺序在同一会话执行，judge 评最后一轮
    java.util.List<String> seenConversationIds = new java.util.ArrayList<>();
    java.util.List<String> seenMessages = new java.util.ArrayList<>();
    ChatService chatService =
        (message, conversationId) -> {
          seenMessages.add(message);
          seenConversationIds.add(conversationId);
          return new ChatAnswer("回答-" + message, List.of());
        };
    EvalJudge judge = (c, answer) -> new EvalJudge.JudgeScore(2, 2, 2, "理由");
    GenerationEvaluator evaluator = new GenerationEvaluator(chatService, judge);
    GenerationCase multiTurn =
        new GenerationCase(
            "其中的 12987 是什么含义",
            "multi-turn",
            "answer-with-source",
            "参照",
            List.of("茅台的酿造工艺是什么", "其中的 12987 是什么含义"));

    List<GenerationEvaluator.GenEvalResult> results = evaluator.evaluate(List.of(multiTurn));

    assertThat(seenMessages).containsExactly("茅台的酿造工艺是什么", "其中的 12987 是什么含义");
    assertThat(seenConversationIds.get(0))
        .as("多轮同会话：两轮 conversationId 必须相同")
        .isEqualTo(seenConversationIds.get(1));
    assertThat(results.getFirst().answer()).isEqualTo("回答-其中的 12987 是什么含义");
  }

  @Test
  void judgeScoreRejectsOutOfRangeValues() {
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class, () -> new EvalJudge.JudgeScore(3, 0, 0, "超界"));
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class, () -> new EvalJudge.JudgeScore(0, -1, 0, "负值"));
  }

  // ── W24 #2 设计性拒答识别 → 检索信号 ──

  private static class SignalCapturingJudge implements EvalJudge {
    boolean seenSignal;

    @Override
    public JudgeScore judge(GenerationCase c, String answer) {
      return new JudgeScore(0, 0, 0, "二参入口");
    }

    @Override
    public JudgeScore judge(GenerationCase c, String answer, boolean emptyRetrievalRefusal) {
      this.seenSignal = emptyRetrievalRefusal;
      return new JudgeScore(0, 2, 2, "带信号");
    }
  }

  @Test
  void emptyRetrievalRefusalMarkedAndSignaled() {
    // RAG 空召回硬拒答（拒答文案 + 零来源）→ 检索信号=true 喂 judge
    ChatService chatService =
        (message, conversationId) ->
            new ChatAnswer(
                com.aievolution.chat.RagChatService.NO_KNOWLEDGE_REPLY + "\n\n免责", List.of());
    SignalCapturingJudge judge = new SignalCapturingJudge();

    new GenerationEvaluator(chatService, judge).evaluate(List.of(sampleCase()));

    assertThat(judge.seenSignal).isTrue();
  }

  @Test
  void agentRedlineRefusalNotMarkedAsEmptyRetrieval() {
    // Agent 通路红线拒答（拒买卖建议，有上下文）文案不同 → 信号=false，判词不串
    ChatService chatService =
        (message, conversationId) -> new ChatAnswer("我无法提供买卖建议。\n\n免责", List.of());
    SignalCapturingJudge judge = new SignalCapturingJudge();

    new GenerationEvaluator(chatService, judge).evaluate(List.of(sampleCase()));

    assertThat(judge.seenSignal).isFalse();
  }

  @Test
  void normalAnswerNotMarkedAsEmptyRetrieval() {
    ChatService chatService =
        (message, conversationId) ->
            new ChatAnswer(
                "营收 1741.44 亿元（来源：年报）",
                List.of(new com.aievolution.chat.SourceDocument("maotai.md", "片段")));
    SignalCapturingJudge judge = new SignalCapturingJudge();

    new GenerationEvaluator(chatService, judge).evaluate(List.of(sampleCase()));

    assertThat(judge.seenSignal).isFalse();
  }
}
