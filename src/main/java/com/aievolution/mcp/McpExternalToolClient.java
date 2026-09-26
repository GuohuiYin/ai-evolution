package com.aievolution.mcp;

import com.aievolution.infra.LogSummaries;
import com.aievolution.tool.ExternalToolClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ExternalToolClient} 的 MCP 协议实现（W12 #1b）：Agent 以 MCP Client 身份调外部服务取数。
 *
 * <p>封装边界：JSON-RPC/stdio/内容类型全部不出本类，对外只有"URL → 文本"。 审计走 {@code tool-audit} 专用
 * logger（ToolAuditAspect 同一通道——模型触发的 每一次数据访问都要可回放，出向调用同标准）；traceId 由控制台 pattern 经 MDC 自动带出。
 *
 * <p>失败哲学：协议错误（isError）、传输异常、参数非法一律返回「抓取失败：…」观察文本， 让模型自我纠正或换工具，不抛异常击穿研究 Loop。
 *
 * <p>W14 #1：连接生命周期委托 {@link McpConnectionManager}——长驻 stdio 会话会静默死亡（M3
 * 门④缺陷），传输异常时重建连接并有界重试一次；协议错误（isError）是对方服务的正常应答，不触发重建。
 */
public class McpExternalToolClient implements ExternalToolClient {

  /** 审计通道与 @Tool 方法同一条（tool-audit 先例），出向入向一份账 */
  private static final Logger auditLog = LoggerFactory.getLogger("tool-audit");

  /** MCP fetch 服务的工具名与入参 schema 是对方服务的协议契约，属协议字面量（A11 允许硬编码） */
  private static final String FETCH_TOOL = "fetch";

  private final McpConnectionManager connectionManager;
  private final int maxContentLength;

  public McpExternalToolClient(McpConnectionManager connectionManager, int maxContentLength) {
    this.connectionManager = connectionManager;
    this.maxContentLength = maxContentLength;
  }

  @Override
  public String fetchWebPage(String url) {
    if (url == null || url.isBlank()) {
      // 空白 URL 不值得一次协议往返（成本闸门），直接给模型可纠正的观察
      return "抓取失败：url 为空或全空白";
    }
    long startNanos = System.nanoTime();
    McpSyncClient client = connectionManager.current();
    if (client == null) {
      // 无活连接（上次重建失败把 current 置空）：先自愈再调用，不拿空连接撞墙
      Optional<McpSyncClient> healed = connectionManager.reconnect("no-live-connection");
      if (healed.isEmpty()) {
        auditLog.warn(
            "tool=fetchWebPage args=[{}] outcome=error elapsedMs={} errorType=NoLiveConnection",
            url,
            elapsedMillis(startNanos));
        return "抓取失败：外部服务连接不可用（连接重建失败）";
      }
      client = healed.get();
    }
    try {
      String observation =
          interpret(client.callTool(new CallToolRequest(FETCH_TOOL, Map.of("url", url))));
      auditSuccess(url, startNanos, observation);
      return observation;
    } catch (RuntimeException e) {
      return reconnectAndRetryOnce(url, startNanos, e);
    }
  }

  /** 传输异常 = 长驻会话可能失活：重建连接并有界重试一次。重建失败保留第一现场异常信息喂回模型； 重试仍失败按重试异常给观察。有界——每次调用最多重建一次，不死循环。 */
  private String reconnectAndRetryOnce(String url, long startNanos, RuntimeException firstError) {
    Optional<McpSyncClient> fresh =
        connectionManager.reconnect(firstError.getClass().getSimpleName());
    if (fresh.isEmpty()) {
      auditError(url, startNanos, firstError);
      return failureObservation(firstError);
    }
    try {
      String observation =
          interpret(fresh.get().callTool(new CallToolRequest(FETCH_TOOL, Map.of("url", url))));
      auditSuccess(url, startNanos, observation);
      return observation;
    } catch (RuntimeException retryError) {
      auditError(url, startNanos, retryError);
      return failureObservation(retryError);
    }
  }

  private void auditSuccess(String url, long startNanos, String observation) {
    auditLog.info(
        "tool=fetchWebPage args=[{}] outcome=success elapsedMs={} resultSummary={}",
        url,
        elapsedMillis(startNanos),
        LogSummaries.summarize(observation));
  }

  private void auditError(String url, long startNanos, RuntimeException e) {
    auditLog.warn(
        "tool=fetchWebPage args=[{}] outcome=error elapsedMs={} errorType={}",
        url,
        elapsedMillis(startNanos),
        e.getClass().getSimpleName());
  }

  private static String failureObservation(RuntimeException e) {
    return "抓取失败：外部服务调用异常（%s: %s）".formatted(e.getClass().getSimpleName(), e.getMessage());
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
