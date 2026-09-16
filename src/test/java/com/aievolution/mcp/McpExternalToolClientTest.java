package com.aievolution.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * W12 #1b：MCP 外部工具客户端单测——协议层（{@link McpSyncClient}）mock 化。
 *
 * <p>锁定的契约：① 请求形状（fetch 工具 + url 入参）② 多段文本聚合 ③ 超长截断带标记
 * ④ 错误一律降级为观察文本喂回模型，不抛异常击穿研究 Loop（ToolRegistry 失败哲学的延伸）。
 */
class McpExternalToolClientTest {

  private final McpSyncClient mcpClient = mock(McpSyncClient.class);
  private final McpExternalToolClient client = new McpExternalToolClient(mcpClient, 100);

  @Test
  void fetchWebPageCallsFetchToolWithUrlArgument() {
    when(mcpClient.callTool(any()))
        .thenReturn(new CallToolResult(List.of(new TextContent("网页正文")), false, null, null));

    String result = client.fetchWebPage("https://example.com");

    ArgumentCaptor<CallToolRequest> captor = ArgumentCaptor.forClass(CallToolRequest.class);
    verify(mcpClient).callTool(captor.capture());
    // MCP fetch 服务的工具名与入参 schema 是协议契约，必须逐字锁定
    assertThat(captor.getValue().name()).isEqualTo("fetch");
    assertThat(captor.getValue().arguments()).containsEntry("url", "https://example.com");
    assertThat(result).isEqualTo("网页正文");
  }

  @Test
  void multipleTextContentsAreJoined() {
    when(mcpClient.callTool(any()))
        .thenReturn(
            new CallToolResult(
                List.of(new TextContent("第一段"), new TextContent("第二段")), false, null, null));

    assertThat(client.fetchWebPage("https://example.com")).isEqualTo("第一段\n第二段");
  }

  @Test
  void overlongContentIsTruncatedWithMarker() {
    String longText = "x".repeat(150);
    when(mcpClient.callTool(any()))
        .thenReturn(new CallToolResult(List.of(new TextContent(longText)), false, null, null));

    String result = client.fetchWebPage("https://example.com");

    assertThat(result).startsWith("x".repeat(100));
    // 截断必须带显式标记：模型需要知道看到的是残文，防止基于残缺信息下结论
    assertThat(result).contains("截断").contains("150");
  }

  @Test
  void protocolErrorBecomesObservationInsteadOfException() {
    when(mcpClient.callTool(any()))
        .thenReturn(
            new CallToolResult(List.of(new TextContent("404 Not Found")), true, null, null));

    String result = client.fetchWebPage("https://example.com/missing");

    assertThat(result).startsWith("抓取失败").contains("404");
  }

  @Test
  void transportExceptionBecomesObservationInsteadOfException() {
    when(mcpClient.callTool(any())).thenThrow(new RuntimeException("connection reset"));

    String result = client.fetchWebPage("https://example.com");

    assertThat(result).startsWith("抓取失败").contains("connection reset");
  }

  @Test
  void emptyContentReturnsExplicitEmptyObservation() {
    when(mcpClient.callTool(any()))
        .thenReturn(new CallToolResult(List.of(), false, null, null));

    // 空结果也要显式——"无输出"与"抓到了空页面"对模型是两个意思
    assertThat(client.fetchWebPage("https://example.com")).isEqualTo("抓取结果为空");
  }

  @Test
  void blankUrlIsRejectedBeforeProtocolCall() {
    assertThat(client.fetchWebPage("  ")).startsWith("抓取失败");
    // 空白 URL 不值得一次协议往返（也是成本闸门）
    org.mockito.Mockito.verifyNoInteractions(mcpClient);
  }
}
