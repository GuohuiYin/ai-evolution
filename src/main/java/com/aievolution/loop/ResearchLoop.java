package com.aievolution.loop;

import java.util.ArrayList;
import java.util.List;

/**
 * 显式 ReAct 研究循环（W11 #1）：把"模型决定 → 调工具 → 再决定"从 Spring AI 内部循环搬到明面上，
 * 轨迹可见、步数可控、中途状态可观测——Harness「Correct」域的系统化起点。
 *
 * <p>串行骨架：每步至多一次工具调用；并行编排是 W14+ 进阶（周计划"明确不做"）。 上限思想复用 {@code
 * spring.ai.tools.limits}：超限优雅退出，不击穿、不编造。
 */
public class ResearchLoop {

  private final ResearchModel model;
  private final ToolExecutor toolExecutor;
  private final int maxSteps;

  public ResearchLoop(ResearchModel model, ToolExecutor toolExecutor, int maxSteps) {
    if (maxSteps < 1) {
      // 上限必须为正，否则循环形同虚设——配置错误 fail-fast（A11：该值是策略，接线处走配置）
      throw new IllegalArgumentException("maxSteps 必须 ≥ 1: " + maxSteps);
    }
    this.model = model;
    this.toolExecutor = toolExecutor;
    this.maxSteps = maxSteps;
  }

  public LoopResult run(String question) {
    List<LoopStep> steps = new ArrayList<>();
    while (steps.size() < maxSteps) {
      ModelTurn turn = model.nextTurn(question, List.copyOf(steps));
      if (turn instanceof ModelTurn.Final f) {
        return new LoopResult(f.answer(), List.copyOf(steps), StopReason.FINAL_ANSWER);
      }
      ModelTurn.Act act = (ModelTurn.Act) turn;
      String observation = toolExecutor.execute(act.tool(), act.input());
      steps.add(new LoopStep(steps.size(), act.thought(), act.tool(), act.input(), observation));
    }
    return new LoopResult(null, List.copyOf(steps), StopReason.MAX_STEPS);
  }
}
