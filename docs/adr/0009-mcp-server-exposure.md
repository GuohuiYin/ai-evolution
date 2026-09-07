# ADR-0009：业务能力以 MCP Server（Streamable HTTP）对外暴露，裸通优先、加固靠 W7

- 状态：已接受
- 日期：2026-09-07（W6-Step2/Step3 落地）

## 背景

M2 验收门要求"业务能力被外部 Agent 客户端真实调用"。需要一个标准化的能力分发方式，使任意 MCP 客户端（Codex/Claude Code/Cursor/Inspector）都能发现并使用本项目的三个工具，而不是每个客户端各写一套适配。

## 决策

1. **协议选型 Streamable HTTP**：现行 MCP 标准传输（旧独立 SSE 已废弃，Codex 等新客户端不再支持）；应用本来就是常驻服务 + k8s 云原生形态，stdio 子进程模式相悖。
2. **暴露层零业务代码**：新建 `mcp` 子域只做协议装配（`McpToolConfiguration` 注册 `ToolCallbackProvider`），schema 与执行完全复用 tool 域 `@Tool` 方法——依赖方向 `mcp → tool` 单向，由 ArchUnit 白名单守护。
3. **裸通优先，加固靠 W7**：MCP 端点可被任意客户端探测，安全面大于自有 Web UI 调用的 `/ai/*`。W6 先完成协议调通（验收门证据），注入攻击实验、错误信息脱敏（`McpExceptionHandler`）、鉴权留到 W7 安全周。这是一个有意识的顺序决策，记录在此防止被误读为"遗漏安全"。
4. **MCP 端点不进 OpenAPI 契约**：协议自带 schema（`tools/list` 即契约），A7 不适用于协议端点；但必须在 README 能力表登记。

## 备选方案

- stdio 传输：需客户端拉起子进程，与常驻服务/k8s 形态相悖。否决。
- 保持 SSE 传输：已被 MCP 规范废弃，Codex 明确不支持。否决。
- 用 `@McpTool` 注解替换 `@Tool`：会造成 tool 域依赖 MCP 语义，破坏"tool 域不知暴露方式"的分层。否决。

## 后果

- 正：Inspector（官方客户端）已真实调通 initialize → tools/list → tools/call 全链路；业务错误走 `isError:true` 而非 HTTP 500，两层错误语义实证分离（参数类型校验失败的案例已演示）。
- 负/待办：Codex CLI 在本机因区域限制（chatgpt.com 403）无法非交互验证，配置（`~/.codex/config.toml [mcp_servers.ai-evolution]`）已就位，待用户在 Codex 桌面端做最终确认；MCP 端点无鉴权，仅可在本机/受信网络暴露（W7 处理）。
