package com.aievolution.tool;

/**
 * 外部工具服务客户端契约（W12 #1，约定 A10 IOP）：Agent 经标准协议调用外部服务取数的端口。
 *
 * <p>与 {@code StockDataClient} 同思想：业务侧（Loop 工具面板）只面向本契约， 协议细节（MCP/stdio/JSON-RPC）由实现方封装在 mcp
 * 域，可替换性即测试性。
 *
 * <p>错误语义约定：实现不得抛异常击穿研究 Loop——一切失败（协议错误/传输异常/ 参数非法）降级为「抓取失败：…」观察文本喂回模型自我纠正（ToolRegistry 失败哲学的延伸）。
 */
public interface ExternalToolClient {

  /**
   * 抓取网页正文（外部 MCP fetch 服务）。
   *
   * @param url 目标网页地址
   * @return 提取后的文本内容；超长按实现配置截断并带显式标记；失败返回错误观察文本
   */
  String fetchWebPage(String url);
}
