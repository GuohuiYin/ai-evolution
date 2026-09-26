package com.aievolution.mcp;

import io.modelcontextprotocol.client.McpSyncClient;

/**
 * MCP fetch 连接工厂（W14 #1）：长驻会话失活后的重建入口——新建传输、完成 initialize 握手， 返回可用客户端。实现负责从既有配置重建，调用方不关心传输形态（stdio
 * / Streamable HTTP）。
 */
public interface McpFetchConnectionFactory {

  /** 建立一条全新的 fetch 连接（含协议握手）；失败抛 RuntimeException，由调用方降级处理 */
  McpSyncClient connect();
}
