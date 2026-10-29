package com.aievolution.cost;

import java.io.UncheckedIOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.core.Ordered;

/**
 * 预算熔断 Advisor（W25 #2b，fail-closed）：每次模型调用前查今日台账， 已用量 ≥ 日预算即抛 {@link BudgetExceededException}
 * 拒答——请求根本不发往模型，熔断的意义就是「超额后零真实调用」。
 *
 * <p>fail-closed（Owner 2026-10-29 拍板）：账本读故障（IO 异常）同样拒答—— 读不出账就无法证明没超预算，宁可误拒不可放行。
 *
 * <p>挂链首（order 先于内容埋点 -2 与记账 -1）：被拒的请求不产生 span 内容、 不落台账（没调用就没有用量）、不占记忆 advisor 的写入。
 *
 * <p>口径：只按 prompt+completion 总量计，调用前检查——故最多超出单次调用的用量 （事后口径，单日几十次量级可接受）。缓存命中价约 1/10 暂不分账折减：v1
 * 从严，多拒不溢。
 */
public class BudgetGuardAdvisor implements CallAdvisor {

  /** 熔断留痕专用 logger：独立于业务日志，可按 logger 名单独订阅告警 */
  private static final Logger budgetLog = LoggerFactory.getLogger("ai.cost.budget");

  private final TokenLedger ledger;
  private final long dailyTokenBudget;

  public BudgetGuardAdvisor(TokenLedger ledger, long dailyTokenBudget) {
    this.ledger = ledger;
    this.dailyTokenBudget = dailyTokenBudget;
  }

  @Override
  public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
    TokenLedger.DailySummary today;
    try {
      today = ledger.summarizeToday();
    } catch (UncheckedIOException e) {
      budgetLog.error("熔断触发（账本不可读，fail-closed 拒答）: {}", e.getMessage());
      throw new BudgetExceededException("token 账本不可读，按 fail-closed 拒答", e);
    }
    long used = today.promptTokens() + today.completionTokens();
    if (used >= dailyTokenBudget) {
      budgetLog.warn(
          "熔断触发（预算用尽）: used={} budget={} calls={}", used, dailyTokenBudget, today.calls());
      throw new BudgetExceededException(
          "今日 token 预算已用尽（已用 " + used + " / 预算 " + dailyTokenBudget + "）");
    }
    return chain.nextCall(request);
  }

  @Override
  public String getName() {
    return "BudgetGuardAdvisor";
  }

  @Override
  public int getOrder() {
    // 链首：先于内容埋点（LOWEST_PRECEDENCE-2）与记账（-1），拒答零副作用
    return Ordered.LOWEST_PRECEDENCE - 3;
  }
}
