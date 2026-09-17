# ADR-0016：MCP Client 出向取数——stdio 官方 fetch 接入，白名单派发而非自动注册

- 状态：已接受
- 日期：2026-09-16（W12 #1 落地）

## 背景

W6 我们做了 MCP Server（别人调我们，ADR-0009）；M3 候选裁决（W9-5）立项 MCP Client——
Agent 以标准协议调用外部服务取数，是研究 Loop"向外取数"的能力底座，也是 M3 验收门之一
（"至少一个外部 MCP 服务被真实调通"）。首个外部服务选型口径：稳定可复现优先于业务贴合。

## 决策

1. **首个外部服务 = 官方 `mcp-server-fetch`（stdio，uvx 拉起）**：公开、无鉴权、单工具，
   协议语义最简单，适合作为 Client 侧第一块实验田；行情/财经类 MCP 服务多为社区三方、
   稳定性与鉴权各异，挂 W14+ 评估。取数场景定位"公告/新闻/研报原文抓取"，与个股研究域贴合。
2. **白名单派发，关闭 toolcallback 自动注册**（`spring.ai.mcp.client.toolcallback.enabled=false`）：
   MCP 工具不进入全局 ToolCallback 池，只经 `ExternalToolClient` 契约（tool 域 IOP）
   由 `ToolRegistry` 显式白名单派发——与 CVE-2026-59318 修复（`tools.resolution.fallback.enabled=false`）
   同一安全立场：请求级工具列表才是真正的派发边界，协议接入不新开隐式通道。
3. **错误降级为观察**：协议错误（isError）、传输异常、参数非法一律返回「抓取失败：…」文本
   喂回模型自我纠正，不抛异常击穿研究 Loop（ToolRegistry 失败哲学的出向延伸）。
   实测实证（2026-09-16）：fetch 连续两轮 30s 超时，模型自主重试一次 → 转查财务数据 →
   终答如实声明网页部分不可得，红线文案完整。
4. **正文长度上限配置化**（`ai.mcp.fetch.max-content-length: 8000`）：网页原文无界，
   截断必须带显式标记——模型需要知道看到的是残文，防基于残缺信息下结论。
5. **子进程环境显式注入 `UV_INDEX_URL` 镜像**：uv 缓存按 index 分桶，默认 PyPI 源在本环境
   冷启动远超 20s 初始化握手超时（实测复现），镜像 ~5s。环境已配同名变量时透传覆盖。

## 备选方案

- **toolcallback 自动注册（库默认开）**：MCP 工具直接进模型工具表，接线最少，
   但派发边界从"请求级白名单"退化为"上下文全局表"，与 W8-1 安全立场冲突。否决。
- **Streamable HTTP 连远程 MCP 服务**：免子进程管理，但首个接入先求协议语义单纯
   （stdio 无网络鉴权变量）；远程连接配置已留档（`spring.ai.mcp.client.streamable-http`），
   有稳定财经 MCP 候选时再评估。挂起。
- **npx 版 fetch（TS 实现）**：与官方 Python 版能力相当；本机 uv 工具链已就绪且
 官方文档以 uvx 为首推，选 uvx。

## 后果

- 正：M3 验收门"外部 MCP 真实调通"达成（2026-09-16 实录：fetchWebPage 1.9s 成功，
  tool-audit + research-trace 双通道 traceId 串联，SSE 轨迹 4 步完整）；
  Agent 工具面板从 3 工具扩为 4 工具（A13 eval 回归 5.5/6 ≥ 基线 5.3/6 无降级）。
- ~~负/待办：stdio 子进程生命周期与 Pod 绑定（K8s 部署时 uvx 需进镜像或换 sidecar/远程 MCP，
  W13 部署冒烟时复核）~~ **已结案（2026-09-17）**：K8s 部署态改走 Streamable HTTP——
  fetch 作为集群内独立 Deployment/Service（`k8s/mcp-fetch.yaml`），官方 stdio 服务器经
  FastMCP 2.14.7 as_proxy 桥成 HTTP（mcp-proxy 与现行 MCP SDK 不兼容、fastmcp 4.x 移除
  as_proxy，实测后钉版）；应用镜像零 Python/uv 依赖，传输按 profile 分文档
  （default=stdio 本地开发，k8s=HTTP）。集群内端到端实测：agent 经桥抓取 example.com
  成功（tool-audit outcome=success，traceId 串联）。业界依据：stdio 是桌面/单客户端
  协议，部署态现行标准是 Streamable HTTP；存量 stdio 服务器用适配层桥接是通行做法。
  首两轮超时根因未完全定论（疑似 TLS/系统缓存冷启动，
  清洁重启后不可复现），挂观察清单。
