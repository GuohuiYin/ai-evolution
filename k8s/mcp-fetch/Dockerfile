# MCP fetch 出向服务镜像（W12 K8s 观察项落地）：
# 官方 mcp-server-fetch 只有 stdio 传输——stdio 是桌面/开发态协议（单客户端、
# 无远程能力），部署态业界标准是 Streamable HTTP。本镜像用 FastMCP 2.x 的
# as_proxy 把 stdio 桥成 streamable-http（本地已实测 initialize + tools/call 全通）。
# 依赖全部构建期 pip 安装，运行时不拉子进程、不需要 uvx/uv（与原方案 C 的本质区别：
# Python 运行在自己的容器里，Java 镜像零膨胀，安全面隔离）。
FROM python:3.13-slim

# 钉版本：fastmcp 4.x 已移除 as_proxy API（本地实测），锁 2.x 最后稳定线
RUN pip install --no-cache-dir \
      -i https://pypi.tuna.tsinghua.edu.cn/simple \
      "fastmcp==2.14.7" \
      "mcp-server-fetch==2026.8.18"

COPY proxy.py /app/proxy.py

# 非 root 运行（A5 最小权限面）
USER 65534:65534
EXPOSE 8000
CMD ["python", "/app/proxy.py"]
