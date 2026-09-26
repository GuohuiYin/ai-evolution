package com.aievolution.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MCP fetch 长驻连接的生命周期管理（W14 #1）：持有当前客户端，失活时关闭旧连接并经 {@link McpFetchConnectionFactory} 重建。
 *
 * <p>缺陷背景（M3 验收门④定论）：长驻 stdio 会话会静默死亡——子进程在但协议无响应， 表现为一律 30s 协议超时；清洁重启应用即恢复。本类把"重启应用"换成"重启连接"。
 *
 * <p>设计取舍（why-not）：不做定时主动 ping——当前单工具低频调用，失活后一次按需重建 （实测独立探测 ~1.6s）代价可接受，定时调度引入的生命周期复杂度不值；调用方有界重试一次。
 */
public class McpConnectionManager {

  /** 审计通道与 @Tool 方法同一条（tool-audit 先例），重连事件同一本账 */
  private static final Logger auditLog = LoggerFactory.getLogger("tool-audit");

  private final McpFetchConnectionFactory connectionFactory;
  private volatile McpSyncClient current;

  public McpConnectionManager(McpSyncClient initial, McpFetchConnectionFactory connectionFactory) {
    this.current = initial;
    this.connectionFactory = connectionFactory;
  }

  /** 当前连接；重建失败后为 null——调用方必须先重建再使用 */
  public McpSyncClient current() {
    return current;
  }

  /** 失活重建：旧连接尽力关闭（已死的连接关闭可能再抛错，吞异常但留痕——静默降级是故障放大器， 只吞异常不吞日志）；重建失败返回空并把 current 置空（下次调用走自愈路径） */
  public synchronized Optional<McpSyncClient> reconnect(String reason) {
    auditLog.warn("tool=fetchWebPage outcome=reconnect reason={}", reason);
    closeQuietly(current);
    try {
      McpSyncClient fresh = connectionFactory.connect();
      if (fresh == null) {
        throw new IllegalStateException("连接工厂返回 null");
      }
      current = fresh;
      auditLog.info("tool=fetchWebPage outcome=reconnect-success");
      return Optional.of(fresh);
    } catch (RuntimeException e) {
      current = null;
      auditLog.warn(
          "tool=fetchWebPage outcome=reconnect-failed errorType={}", e.getClass().getSimpleName());
      return Optional.empty();
    }
  }

  private static void closeQuietly(McpSyncClient client) {
    if (client == null) {
      return;
    }
    try {
      client.closeGracefully();
    } catch (RuntimeException e) {
      auditLog.warn(
          "tool=fetchWebPage outcome=close-stale-failed errorType={}",
          e.getClass().getSimpleName());
    }
  }
}
