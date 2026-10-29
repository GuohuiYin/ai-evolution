package com.aievolution.cost;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 成本仪表盘端点（W25 #2a-ii）：今日 token 用量聚总——总量 + 按模型/按通路两维。
 *
 * <p>数据源即本地 JSONL 台账（{@link TokenLedger}），与 #2b 预算熔断同源同账： 展示的数字就是熔断的依据，杜绝「展示一套账、熔断一套账」。
 *
 * <p>返回体的 byModel/byChannel 桶值为二元数组 {@code [totalTokens, calls]}。
 */
@Tag(name = "Cost", description = "token 成本台账")
@RestController
@RequestMapping("/ai/cost")
public class CostController {

  private final TokenLedger ledger;

  public CostController(TokenLedger ledger) {
    this.ledger = ledger;
  }

  @Operation(
      summary = "今日 token 用量汇总",
      description = "按本地时区界天聚总：总调用次数、prompt/completion/缓存命中 token 总量，及按模型、按通路两个维度的拆分")
  @ApiResponse(responseCode = "200", description = "成功返回当日汇总")
  @GetMapping("/summary")
  public TokenLedger.DailySummary today() {
    return ledger.summarizeToday();
  }
}
