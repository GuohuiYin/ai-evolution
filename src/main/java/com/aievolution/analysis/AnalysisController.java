package com.aievolution.analysis;

import com.aievolution.compliance.Disclaimers;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** P0 个股历史表现分析端点（约定 A7：OpenAPI 文档即契约）。 */
@RestController
@RequestMapping("/ai")
@Tag(name = "个股分析", description = "P0：基于数据源客户端的结构化历史表现分析")
public class AnalysisController {

  /** 红线 01：免责声明由服务端强制附带，引用全项目单点定义（约定 A12）。 */
  private static final String DISCLAIMER = Disclaimers.AI_GENERATED;

  private final StockAnalysisService analysisService;

  public AnalysisController(StockAnalysisService analysisService) {
    this.analysisService = analysisService;
  }

  @Operation(
      summary = "个股历史表现结构化分析",
      description = "拉取数据源客户端的行情与财务数据，由模型输出结构化分析报告（仅历史事实，不含预测与买卖建议）")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "分析成功"),
    @ApiResponse(responseCode = "400", description = "请求参数非法（如不支持的 prompt 版本）"),
    @ApiResponse(responseCode = "404", description = "数据源未覆盖该股票代码"),
    @ApiResponse(responseCode = "502", description = "上游模型调用失败")
  })
  @PostMapping("/analyze")
  public AnalyzeResponse analyze(
      @Parameter(description = "股票代码，如 600519", example = "600519") @RequestParam String code,
      @Parameter(description = "prompt 版本（v0=zero-shot 基线，v1=CO-STAR+few-shot）", example = "v1")
          @RequestParam(defaultValue = "v1")
          String promptVersion) {
    return new AnalyzeResponse(analysisService.analyze(code, promptVersion), DISCLAIMER);
  }

  @Schema(description = "分析响应：结构化报告 + 服务端强制附带的免责声明")
  public record AnalyzeResponse(
      @Schema(description = "结构化分析报告") StockAnalysisReport report,
      @Schema(description = "免责声明（金融红线一，服务端追加）") String disclaimer) {}
}
