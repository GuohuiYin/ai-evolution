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
    ChatService chatService = message -> new ChatAnswer("被测回答：" + message, List.of());
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
  void judgeScoreRejectsOutOfRangeValues() {
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class, () -> new EvalJudge.JudgeScore(3, 0, 0, "超界"));
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class, () -> new EvalJudge.JudgeScore(0, -1, 0, "负值"));
  }
}
