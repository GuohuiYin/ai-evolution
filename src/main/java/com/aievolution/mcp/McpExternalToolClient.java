package com.aievolution.mcp;

import com.aievolution.infra.LogSummaries;
import com.aievolution.tool.ExternalToolClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ExternalToolClient} 的 MCP 协议实现（W12 #1b）：Agent 以 MCP Client 身份调外部服务取数。
 *
 * <p>封装边界：JSON-RPC/stdio/内容类型全部不出本类，对外只有"URL → 文本"。 审计走 {@code tool-audit} 专用 logger（ToolAuditAspect 同一通道——模型触发的
 * 每一次数据访问都要可回放，出向调用同标准）；traceId 由控制台 pattern 经 MDC 自动带出。
 *
 * <p>失败哲学：协议错误（isError）、传输异常、参数非法一律返回「抓取失败：…」观察文本， 让模型自我纠正或换工具，不抛异常击穿研究 Loop。
 */
public class McpExternalToolClient implements ExternalToolClient {

  /** 审计通道与 @Tool 方法同一条（tool-audit 先例），出向入向一份账 */
  private static final Logger auditLog = LoggerFactory.getLogger("tool-audit");

  /** MCP fetch 服务的工具名与入参 schema 是对方服务的协议契约，属协议字面量（A11 允许硬编码） */
  private static final String FETCH_TOOL = "fetch";

  private final McpSyncClient mcpClient;
  private final int maxContentLength;

  public McpExternalToolClient(McpSyncClient mcpClient, int maxContentLength) {
    this.mcpClient = mcpClient;
    this.maxContentLength = maxContentLength;
  }

  @Override
  public String fetchWebPage(String url) {
    if (url == null || url.isBlank()) {
      // 空白 URL 不值得一次协议往返（成本闸门），直接给模型可纠正的观察
      return "抓取失败：url 为空或全空白";
    }
    long startNanos = System.nanoTime();
    try {
      CallToolResult result =
          mcpClient.callTool(new CallToolRequest(FETCH_TOOL, Map.of("url", url)));
      String observation = interpret(result);
      auditLog.info(
          "tool=fetchWebPage args=[{}] outcome=success elapsedMs={} resultSummary={}",
          url,
          elapsedMillis(startNanos),
          LogSummaries.summarize(observation));
      return observation;
    } catch (RuntimeException e) {
      auditLog.warn(
          "tool=fetchWebPage args=[{}] outcome=error elapsedMs={} errorType={}",
          url,
          elapsedMillis(startNanos),
          e.getClass().getSimpleName());
      return "抓取失败：外部服务调用异常（%s: %s）".formatted(e.getClass().getSimpleName(), e.getMessage());
    }
  }

  /** 协议结果 → 观察文本：isError 与正文抽取集中于此，喂回模型的措辞单点定义 */
  private String interpret(CallToolResult result) {
    String text = extractText(result);
    if (Boolean.TRUE.equals(result.isError())) {
      return "抓取失败：外部服务返回错误（%s）".formatted(text);
    }
    if (text.isBlank()) {
      return "抓取结果为空";
    }
    if (text.length() > maxContentLength) {
      // 截断必须带显式标记：模型需要知道看到的是残文，防止基于残缺信息下结论
      return text.substring(0, maxContentLength) + "\n[已截断，原文共 %d 字符]".formatted(text.length());
    }
    return text;
  }

  /** 多段文本内容聚合；非文本段（图片/资源）对当前文本通路无意义，略过 */
  private static String extractText(CallToolResult result) {
    if (result.content() == null) {
      return "";
    }
    return result.content().stream()
        .filter(TextContent.class::isInstance)
        .map(c -> ((TextContent) c).text())
        .collect(Collectors.joining("\n"));
  }

  private static long elapsedMillis(long startNanos) {
    // 审计计时用 nanoTime（tool-audit 先例，单调时钟防系统回拨）
    return (System.nanoTime() - startNanos) / 1_000_000;
  }
}
