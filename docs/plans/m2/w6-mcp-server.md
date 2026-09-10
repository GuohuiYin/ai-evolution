# W6：MCP 协议精读 + 动手（P2）

> 补录于 2026-09-10（当周未单独立档，自 commit 记录与阶段概要回填要点）。
> 核心交付：**业务能力封装为 MCP Server，被真实客户端调通**。

## 当周交付

- MCP 协议精读笔记先行（先读懂协议再动手）：[docs/notes/mcp-protocol.md](../../notes/mcp-protocol.md)
- MCP Server 落地：Streamable HTTP（现行标准，旧 SSE 已废弃），`POST /mcp` 与 `/ai/*` 业务命名空间隔离
  - 三工具暴露：getDailyQuotes / getFinancialSummary / searchAnnouncements
  - ADR-0009（裸通优先、加固靠 W7）
- 协议级冒烟脚本：docs/scripts/mcp-smoke.sh（initialize → initialized → tools/list → tools/call 全链路断言）
- Inspector 真实调通；Codex 接入配置（`~/.codex/config.toml`）
- **对话统一入口 + 规则路由**（W5 遗留裁决落地，ADR-0010）：`/ai/chat` 自动分发 RAG/Agent 双通路，`/ai/agent` 保留显式旁路
- 可观测性三步：TraceIdFilter 入 MDC / 路由日志 / 检索日志，traceId 串链
- 默认端口统一 18080（8080 常被 IDE 占用），全链路含 k8s 对齐

## 验收回看

M2 验收门①"被外部客户端调通"的证据链全部产出于当周。
