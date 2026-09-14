package com.aievolution.loop;

import com.aievolution.tool.AnnouncementTools;
import com.aievolution.tool.StockDataTools;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

/**
 * Loop 工具注册表（W11 #3a）：把现有三工具（W5 起模型自主调用的那套）按名桥接进显式循环， 实现 {@link ToolExecutor} 端口。
 *
 * <p>协议：模型输出 {@code Action: <工具名>} + {@code Action Input: <JSON 对象>}。 注册表负责解析 JSON
 * 并派发到强类型方法——显式白名单派发，无反射魔法， 工具集合的边界就是三个 case（呼应 W8-1 工具解析兜底显式关闭的安全立场）。
 *
 * <p>失败哲学：不可派发（未知工具/JSON 畸形/缺参）一律降级为错误观察喂回模型， 让模型自我纠正；不抛异常击穿循环（同 {@code RETURN_ERROR_RESPONSE}
 * 不击穿思想）。
 */
public class ToolRegistry implements ToolExecutor {

  /** 工具手册条目：名称 + 用途 + 入参 schema 说明，供 #3b 拼系统 prompt */
  public record ToolSpec(String name, String description, String paramsSchema) {}

  private final StockDataTools stockDataTools;
  private final AnnouncementTools announcementTools;
  // 入参反序列化：局部静态实例即可（ChatController 先例）
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final List<ToolSpec> specs;

  public ToolRegistry(StockDataTools stockDataTools, AnnouncementTools announcementTools) {
    this.stockDataTools = stockDataTools;
    this.announcementTools = announcementTools;
    // 描述与 W5 @Tool 注解同义：模型的使用手册，改动视同 prompt 资产变更（A13 纪律）
    this.specs =
        List.of(
            new ToolSpec(
                "getDailyQuotes",
                "查询指定股票的区间日行情（收盘价，单位：元）。问到股价、行情、走势时使用",
                "{\"code\":\"股票代码，如 600519\",\"from\":\"开始日期 ISO，如 2024-01-01\",\"to\":\"结束日期 ISO\"}"),
            new ToolSpec(
                "getFinancialSummary",
                "查询指定股票的年度财务摘要（营收/净利润，单位：亿元）。问到营收、利润、财务表现时使用",
                "{\"code\":\"股票代码\",\"fiscalYear\":会计年度整数，如 2024}"),
            new ToolSpec(
                "searchAnnouncements",
                "检索知识库中的公司公告与研究资料（语义检索）。涉及公司业务、工艺、战略等非数字信息时使用",
                "{\"query\":\"检索问题，用完整问句效果更好\"}"));
  }

  public List<ToolSpec> specs() {
    return specs;
  }

  @Override
  public String execute(String tool, String input) {
    JsonNode args;
    try {
      args = OBJECT_MAPPER.readTree(input);
    } catch (JsonProcessingException e) {
      return "入参解析失败：Action Input 必须是 JSON 对象（%s）".formatted(e.getOriginalMessage());
    }
    try {
      return switch (tool) {
        case "getDailyQuotes" ->
            stockDataTools.getDailyQuotes(text(args, "code"), text(args, "from"), text(args, "to"));
        case "getFinancialSummary" ->
            stockDataTools.getFinancialSummary(text(args, "code"), integer(args, "fiscalYear"));
        case "searchAnnouncements" -> announcementTools.searchAnnouncements(text(args, "query"));
        default ->
            "未知工具：%s。可用工具：getDailyQuotes / getFinancialSummary / searchAnnouncements"
                .formatted(tool);
      };
    } catch (IllegalArgumentException e) {
      return "入参解析失败：%s".formatted(e.getMessage());
    }
  }

  private static String text(JsonNode args, String field) {
    JsonNode node = args.get(field);
    if (node == null || !node.isTextual()) {
      throw new IllegalArgumentException("缺少字符串参数 " + field);
    }
    return node.asText();
  }

  private static int integer(JsonNode args, String field) {
    JsonNode node = args.get(field);
    if (node == null || !node.canConvertToInt()) {
      throw new IllegalArgumentException("缺少整数参数 " + field);
    }
    return node.asInt();
  }
}
