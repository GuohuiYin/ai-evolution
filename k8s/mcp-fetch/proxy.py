"""stdio → Streamable HTTP 桥：官方 mcp-server-fetch 只讲 stdio（桌面协议），
经 FastMCP 2.x as_proxy 暴露为 /mcp（部署态现行标准）。
与本地 uvx 直拉同一份 mcp-server-fetch 包，工具契约（名字/schema）完全一致。
"""
from fastmcp import FastMCP
from fastmcp.client import Client
from fastmcp.client.transports import StdioTransport

# 镜像内 pip 安装的 mcp-server-fetch 二进制，无 uvx 子进程链路
proxy = FastMCP.as_proxy(
    Client(StdioTransport("mcp-server-fetch", [])), name="fetch-proxy"
)

if __name__ == "__main__":
    proxy.run(transport="streamable-http", host="0.0.0.0", port=8000)
