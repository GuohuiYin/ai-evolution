package com.aievolution.eval;

import com.aievolution.chat.ChatAnswer;
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
    if (c.isMultiTurn()) {
      // W11 #6：多轮脚本同会话顺序执行，评末轮——多轮能力的评测集本体（指代消解/追问深化/话题切换）
      String conversationId = "eval-" + UUID.randomUUID();
      ChatAnswer answer = null;
      for (String turn : c.turns()) {
        answer = chatService.chat(turn, conversationId);
      }
      return new GenEvalResult(
          c, answer.reply(), judge.judge(c, answer.reply(), isEmptyRetrievalRefusal(answer)));
    }
    // 每条用例独立会话：eval 测单轮能力，记忆不串题
    ChatAnswer answer = chatService.chat(c.query(), "eval-" + UUID.randomUUID());
    return new GenEvalResult(
        c, answer.reply(), judge.judge(c, answer.reply(), isEmptyRetrievalRefusal(answer)));
  }

  /** 设计性拒答识别（W24 #2）：RAG 空召回硬拒答文案 + 零来源 → 检索信号喂 judge。 Agent 通路红线拒答（有上下文仍拒买卖建议）不匹配本信号——两者判词不同。 */
  private static boolean isEmptyRetrievalRefusal(ChatAnswer answer) {
    return answer.sources().isEmpty()
        && answer.reply().startsWith(com.aievolution.chat.RagChatService.NO_KNOWLEDGE_REPLY);
  }

  /**
   * @param answer 被测回答原文（报告中完整展示——实测先行的模板约定）
   */
  public record GenEvalResult(
      GenerationCase goldenCase, String answer, EvalJudge.JudgeScore score) {}
}
