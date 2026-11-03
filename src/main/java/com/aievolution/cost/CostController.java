package com.aievolution.cost;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 成本仪表盘端点（W25 #2a-ii）：今日 token 用量聚总——总量 + 按模型/按通路两维。
 *
 * <p>数据源即本地 JSONL 台账（{@link TokenLedger}），与 #2b 预算熔断同源同账： 展示的数字就是熔断的依据，杜绝「展示一套账、熔断一套账」。
 *
 * <p>返回体的 byModel/byChannel 桶值为二元数组 {@code [totalTokens, calls]}。
 *
 * <p>W27 #4：预算运行时可调——{@code POST /budget} 撞线救场当日生效、次日自动回落， {@code GET /budget} 查当前生效值与调整留痕（W26 报告
 * P5/S1）。
 */
@Tag(name = "Cost", description = "token 成本台账")
@RestController
@RequestMapping("/ai/cost")
public class CostController {

  private final TokenLedger ledger;
  private final DailyBudgetOverride budget;

  public CostController(TokenLedger ledger, DailyBudgetOverride budget) {
    this.ledger = ledger;
    this.budget = budget;
  }

  @Operation(
      summary = "今日 token 用量汇总",
      description = "按本地时区界天聚总：总调用次数、prompt/completion/缓存命中 token 总量，及按模型、按通路两个维度的拆分")
  @ApiResponse(responseCode = "200", description = "成功返回当日汇总")
  @GetMapping("/summary")
  public TokenLedger.DailySummary today() {
    return ledger.summarizeToday();
  }

  /** 调预算请求体：reason 可选但建议带——调整留痕是审计素材 */
  public record BudgetAdjustRequest(@Positive(message = "预算值必须为正数") long budget, String reason) {}

  /** 预算状态：生效值 + 配置默认 + 当日调整留痕（无调整或已过期则 adjustment 为 null） */
  public record BudgetState(
      long effectiveBudget, long configuredDefault, DailyBudgetOverride.Adjustment adjustment) {}

  @Operation(
      summary = "运行时调整当日 token 预算",
      description = "撞线救场入口：调整即时生效无需重启，当日有效、次日自动回落配置默认；调整值落盘供重启回放")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "调整成功，返回生效后的预算状态"),
    @ApiResponse(responseCode = "400", description = "预算值非法（非正数）")
  })
  @PostMapping("/budget")
  public BudgetState adjustBudget(@Valid @RequestBody BudgetAdjustRequest request) {
    budget.adjust(request.budget(), request.reason());
    return budgetState();
  }

  @Operation(summary = "当前预算状态", description = "生效预算 + 配置默认 + 当日调整留痕（若有）；重启后确认回放走此端点")
  @ApiResponse(responseCode = "200", description = "成功返回预算状态")
  @GetMapping("/budget")
  public BudgetState budgetState() {
    return new BudgetState(
        budget.effectiveBudget(), budget.configuredDefault(), budget.currentAdjustment());
  }
}
