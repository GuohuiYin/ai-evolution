package com.aievolution.eval;

import com.aievolution.chat.ChatService;
import java.util.List;
import java.util.UUID;

/**
 * 生成层评估器（W8-4）：黄金集问题 → 生产统一入口（{@link ChatService}，含路由）取回答 → {@link EvalJudge}
 * 三维评分。评的就是用的——回答必须来自线上同一条通路。
 */
public class GenerationEvaluator {

  private final ChatService chatService;
  private final EvalJudge judge;

  public GenerationEvaluator(ChatService chatService, EvalJudge judge) {
    this.chatService = chatService;
    this.judge = judge;
  }

  public List<GenEvalResult> evaluate(List<GenerationCase> cases) {
    return cases.stream().map(this::evaluateOne).toList();
  }

  private GenEvalResult evaluateOne(GenerationCase c) {
    // 每条用例独立会话：eval 测单轮能力，记忆不串题（多轮评测集 W11 另行设计）
    String answer = chatService.chat(c.query(), "eval-" + UUID.randomUUID()).reply();
    return new GenEvalResult(c, answer, judge.judge(c, answer));
  }

  /**
   * @param answer 被测回答原文（报告中完整展示——实测先行的模板约定）
   */
  public record GenEvalResult(
      GenerationCase goldenCase, String answer, EvalJudge.JudgeScore score) {}
}
