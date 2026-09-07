package com.aievolution.chat;

import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 对话路由（W6 统一入口裁决）：决定一条消息走哪条通路。
 *
 * <p>规则（有意保持简单、可解释、可测试）：
 *
 * <ul>
 *   <li>含 6 位股票代码 或 取数关键词（股价/行情/营收等）→ {@link Route#AGENT}（工具实时取数）
 *   <li>其余（纯知识问答）→ {@link Route#RAG}（检索管道，保留硬拒答闸门与结构化 sources）
 * </ul>
 *
 * <p>关键词是策略不是常量（约定 A11）：外置 {@code ai.chat.agent-keywords}，调参不改代码。 已知边界：含"年报"但无取数意图的问题仍走 RAG；误路由到
 * AGENT 也不致命（工具是超集能力）， 误路由到 RAG 才会丢能力——所以规则宁可偏 AGENT。详见 ADR-0010。
 */
@Component
public class ChatRouter {

  public enum Route {
    RAG,
    AGENT
  }

  /** A 股代码形态：独立出现的 6 位数字（\b 防止误伤长数字串/年份片段）。 */
  private static final Pattern STOCK_CODE = Pattern.compile("\\b\\d{6}\\b");

  private static final Logger log = LoggerFactory.getLogger(ChatRouter.class);

  private final List<String> agentKeywords;

  public ChatRouter(
      @Value("${ai.chat.agent-keywords:股价,行情,走势,营收,净利,利润,财务}") List<String> agentKeywords) {
    this.agentKeywords = agentKeywords;
  }

  public Route route(String message) {
    if (message == null || message.isBlank()) {
      return logDecision(Route.RAG, "blank", message);
    }
    if (STOCK_CODE.matcher(message).find()) {
      return logDecision(Route.AGENT, "stock_code", message);
    }
    return agentKeywords.stream()
        .filter(message::contains)
        .findFirst()
        .map(keyword -> logDecision(Route.AGENT, "keyword:" + keyword, message))
        .orElseGet(() -> logDecision(Route.RAG, "fallback", message));
  }

  /** 路由决策唯一日志出口（A12 单点化）：排查"为什么走这条路"只看这里。 */
  private Route logDecision(Route route, String reason, String message) {
    log.info("stage=ROUTE route={} reason={} msg={}", route, reason, abbreviate(message));
    return route;
  }

  private static String abbreviate(String message) {
    if (message == null) {
      return "";
    }
    String oneLine = message.replaceAll("\\s+", " ").trim();
    return oneLine.length() <= 30 ? oneLine : oneLine.substring(0, 30) + "…";
  }
}
