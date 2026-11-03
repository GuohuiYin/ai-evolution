package com.aievolution.eval;

import com.aievolution.chat.ChatAnswer;
import com.aievolution.chat.ChatService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 生成层评估器（W8-4）：黄金集问题 → 生产统一入口（{@link ChatService}，含路由）取回答 → {@link EvalJudge}
 * 三维评分。评的就是用的——回答必须来自线上同一条通路。
 *
 * <p>单案故障隔离（W27 #3a）：一案抛异常（如供应商断流打穿 activity 重试）登记 {@link EvalFailure} 并继续，
 * 不再牺牲全批——失败案不进均分、报告段单列、退出码非零（故障有声，静默降级是故障放大器）。
 */
public class GenerationEvaluator {

  private static final Logger log = LoggerFactory.getLogger(GenerationEvaluator.class);

  private final ChatService chatService;
  private final EvalJudge judge;

  public GenerationEvaluator(ChatService chatService, EvalJudge judge) {
    this.chatService = chatService;
    this.judge = judge;
  }

  public EvalOutcome evaluate(List<GenerationCase> cases) {
    List<GenEvalResult> results = new ArrayList<>();
    List<EvalFailure> failures = new ArrayList<>();
    for (GenerationCase c : cases) {
      try {
        results.add(evaluateOne(c));
      } catch (Exception e) {
        // 隔离单案：批的价值是整体分布，一案断流不该抹掉其余 57 案（W27 全量批 40/58 夭折教训）
        log.error(
            "生成 eval 单案失败 category={} query={} errorType={}",
            c.category(),
            c.query(),
            e.getClass().getSimpleName());
        failures.add(new EvalFailure(c, e.getClass().getSimpleName()));
      }
    }
    return new EvalOutcome(List.copyOf(results), List.copyOf(failures));
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

  /** 批次结果：成功案 + 失败案分列（失败案不进均分，防止故障被评分稀释成静默） */
  public record EvalOutcome(List<GenEvalResult> results, List<EvalFailure> failures) {}

  /** 单案失败登记：异常类型留痕（errorType 而非堆栈——归因到故障类别即可，堆栈在日志） */
  public record EvalFailure(GenerationCase goldenCase, String errorType) {}
}
