package com.aievolution.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.modelcontextprotocol.client.McpSyncClient;
import org.junit.jupiter.api.Test;

/**
 * W14 #1：连接生命周期管理单测。缺陷背景（M3 验收门④）：长驻 stdio 会话静默死亡—— 子进程在但协议无响应，一律 30s 超时；清洁重启恢复。本类把"重启应用"换成"重启连接"。
 */
class McpConnectionManagerTest {

  private final McpFetchConnectionFactory connectionFactory = mock(McpFetchConnectionFactory.class);

  @Test
  void reconnectClosesStaleClientAndReturnsFreshOne() {
    McpSyncClient stale = mock(McpSyncClient.class);
    McpSyncClient fresh = mock(McpSyncClient.class);
    when(connectionFactory.connect()).thenReturn(fresh);
    McpConnectionManager manager = new McpConnectionManager(stale, connectionFactory);

    var result = manager.reconnect("test");

    assertThat(result).contains(fresh);
    assertThat(manager.current()).isSameAs(fresh);
    // 旧连接必须尽力关闭：stdio 子进程不回收就是泄漏
    verify(stale).closeGracefully();
  }

  @Test
  void reconnectFailureReturnsEmptyAndClearsCurrent() {
    McpSyncClient stale = mock(McpSyncClient.class);
    when(connectionFactory.connect()).thenThrow(new RuntimeException("uvx not found"));
    McpConnectionManager manager = new McpConnectionManager(stale, connectionFactory);

    var result = manager.reconnect("test");

    assertThat(result).isEmpty();
    // 重建失败后 current 置空：下一次调用走"无活连接先重建"路径，而不是拿死连接再撞一次
    assertThat(manager.current()).isNull();
  }

  @Test
  void reconnectWithNullCurrentSkipsClose() {
    McpSyncClient fresh = mock(McpSyncClient.class);
    when(connectionFactory.connect()).thenReturn(fresh);
    McpConnectionManager manager = new McpConnectionManager(null, connectionFactory);

    assertThat(manager.reconnect("test")).contains(fresh);
  }

  @Test
  void factoryReturningNullIsTreatedAsFailure() {
    when(connectionFactory.connect()).thenReturn(null);
    McpConnectionManager manager = new McpConnectionManager(null, connectionFactory);

    assertThat(manager.reconnect("test")).isEmpty();
  }
}
