package com.aievolution.loop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** W11 #1：ReAct 循环骨架。三条退出路径—— 正常收敛（工具调用后模型给终答）/ 达上限退出（防死循环烧 token） / 首轮终答（零工具调用直通）。 */
class ResearchLoopTest {

  /** 剧本式模型桩：按队列依次出招，队列空则报错（防测试 silent pass）；合成步默认空手（退兜底） */
  private static final class ScriptedModel implements ResearchModel {
    private final Deque<ModelTurn> script = new ArrayDeque<>();
    private final List<List<LoopStep>> seenHistory = new ArrayList<>();
    private Optional<String> synthesis = Optional.empty();
    private List<LoopStep> synthesisSeenHistory;
    private int synthesisCalls;

    ScriptedModel(ModelTurn... turns) {
      script.addAll(List.of(turns));
    }

    ScriptedModel withSynthesis(String answer) {
      this.synthesis = Optional.of(answer);
      return this;
    }

    @Override
    public ModelTurn nextTurn(String question, List<LoopStep> history) {
      seenHistory.add(List.copyOf(history));
      if (script.isEmpty()) {
        throw new IllegalStateException("剧本已耗尽，loop 不该再问模型");
      }
      return script.poll();
    }

    @Override
    public Optional<String> synthesize(String question, List<LoopStep> history) {
      synthesisCalls++;
      synthesisSeenHistory = List.copyOf(history);
      return synthesis;
    }
  }

  private static ModelTurn act(String tool, String input) {
    return new ModelTurn.Act("想一下", tool, input);
  }

  private static ModelTurn finish(String answer) {
    return new ModelTurn.Final("总结", answer);
  }

  @Test
  void convergesAfterToolCallsThenFinalAnswer() {
    ScriptedModel model = new ScriptedModel(act("getQuote", "600519"), finish("茅台收盘价 1700"));
    ResearchLoop loop = new ResearchLoop(model, (tool, input) -> "1700.00", 5);

    LoopResult result = loop.run("茅台现在多少钱？");

    assertThat(result.stopReason()).isEqualTo(StopReason.FINAL_ANSWER);
    assertThat(result.answer()).isEqualTo("茅台收盘价 1700");
    assertThat(result.steps()).hasSize(1);
    LoopStep step = result.steps().get(0);
    assertThat(step.index()).isZero();
    assertThat(step.tool()).isEqualTo("getQuote");
    assertThat(step.observation()).isEqualTo("1700.00");
    // 第二轮模型应看到带观察的完整历史（observation 回喂）
    assertThat(model.seenHistory.get(1)).hasSize(1);
  }

  @Test
  void stopsGracefullyAtMaxSteps() {
    ScriptedModel model = new ScriptedModel(act("getQuote", "600519"), act("getQuote", "600519"));
    ResearchLoop loop = new ResearchLoop(model, (tool, input) -> "obs", 2);

    LoopResult result = loop.run("追不完的问题");

    assertThat(result.stopReason()).isEqualTo(StopReason.MAX_STEPS);
    assertThat(result.steps()).hasSize(2);
    // 合成步已尝试但空手（模型桩默认）→ answer 为空，措辞交调用方兜底（W20 #1 裁决①B）
    assertThat(model.synthesisCalls).isEqualTo(1);
    assertThat(model.synthesisSeenHistory).hasSize(2);
    assertThat(result.answer()).isNull();
  }

  @Test
  void synthesizesAnswerWhenMaxStepsExhausted() {
    // W20 #1 裁决①B：触顶追加无工具合成步——已查证 observations 喂回模型强制收尾
    ScriptedModel model =
        new ScriptedModel(act("getQuote", "600519"), act("getQuote", "600519"))
            .withSynthesis("已查证部分：营收 100 亿（来源：mock，时点：2025-04-30）");
    ResearchLoop loop = new ResearchLoop(model, (tool, input) -> "obs", 2);

    LoopResult result = loop.run("追不完的问题");

    assertThat(result.stopReason()).isEqualTo(StopReason.MAX_STEPS);
    assertThat(result.answer()).isEqualTo("已查证部分：营收 100 亿（来源：mock，时点：2025-04-30）");
    // 合成步看到完整轨迹（2 步 observations 全部回喂）
    assertThat(model.synthesisSeenHistory).hasSize(2);
  }

  @Test
  void exitsImmediatelyWhenModelAnswersDirectly() {
    ScriptedModel model = new ScriptedModel(finish("直接回答"));
    List<String> toolCalls = new ArrayList<>();
    ResearchLoop loop =
        new ResearchLoop(
            model,
            (tool, input) -> {
              toolCalls.add(tool);
              return "obs";
            },
            5);

    LoopResult result = loop.run("不用查资料的问题");

    assertThat(result.stopReason()).isEqualTo(StopReason.FINAL_ANSWER);
    assertThat(result.answer()).isEqualTo("直接回答");
    assertThat(result.steps()).isEmpty();
    assertThat(toolCalls).isEmpty();
  }

  @Test
  void rejectsNonPositiveMaxSteps() {
    ScriptedModel model = new ScriptedModel();
    assertThatThrownBy(() -> new ResearchLoop(model, (t, i) -> "", 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxSteps");
  }
}
