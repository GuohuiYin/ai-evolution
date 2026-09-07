# MCP 协议精读笔记（W6-Step1）

> 目标：不写代码之前先读懂协议，回答一个核心问题——**为什么 MCP 是协议，而不是又一个 Function Calling API**。
> 依据：MCP 官方规范（2025-06-18 修订版）+ Spring AI MCP Server 文档。
> 退出条件：本文覆盖三原语、生命周期、两种 transport、与 Function Calling 的本质差异，并落到本项目 Step2 的设计决策。

---

## 1. 一句话定义

MCP（Model Context Protocol）是 Anthropic 2024 年 11 月开源的**应用层协议**，规范了
"AI 客户端（Host/Client）" 与 "能力提供方（Server）" 之间如何发现能力、传递上下文、发起调用。
传输格式是 **JSON-RPC 2.0**，与语言无关。

类比：MCP 之于 AI 应用，相当于 **JDBC 之于数据库访问 / LSP 之于 IDE**。
LSP 之前每个编辑器要为每种语言写适配；MCP 之前每个 Agent 框架要为每个工具源写适配。
协议的价值不在"能调通"，在"**N 个客户端 × M 个服务 退化为 N+M 个实现**"。

## 2. 核心问题：MCP vs Function Calling 的本质差异

| 维度 | Function Calling（如 DeepSeek/OpenAI tools） | MCP |
|---|---|---|
| 层级 | **模型 API 的特性**（某一次请求体里的 `tools` 字段） | **独立协议**（有生命周期、有传输层、有能力协商） |
| 工具归谁管 | 应用进程内：schema 拼进 prompt，回包后应用自己执行 | Server 进程/服务：client 只发 `tools/call`，执行在 server 侧 |
| 发现机制 | 无——每次请求全量携带 schema | `tools/list` 动态发现，可热更新（`notifications/tools/list_changed`） |
| 跨进程/跨机器 | 做不到（本质是 HTTP 请求体的一部分） | 原生支持（stdio 本机子进程 / Streamable HTTP 远程服务） |
| 生态复用 | 每个应用各自实现 | 一个 server 被所有 MCP 客户端（Claude Code/Codex/Cursor…）复用 |

**结论**：Function Calling 是"模型调用能力"，MCP 是"能力分发协议"。
我们 W5 做的三工具 Function Calling 解决的是"DeepSeek 会调工具"；
W6 做 MCP Server 解决的是"**任何 MCP 客户端都能发现并使用我们的工具**"——
能力从"租户的私有实现"变成"协议上的公共服务"。
两者不互斥：MCP server 内部仍可用 Function Calling 调模型；我们的情况是**反向**——把已有工具层对外暴露。

## 3. 三原语（Server 能提供的三类能力）

| 原语 | 控制方 | 语义 | 本项目映射 |
|---|---|---|---|
| **Tools** | 模型决定调用 | 有副作用或计算的函数，schema = JSON Schema | ✅ 现有三个 `@Tool`：`searchKnowledge` / `queryStockPrice` / `generateAnalysisReport` |
| **Resources** | 应用/用户选择 | 只读上下文数据（URI 寻址，如 `file://`、`db://`），可订阅变更 | 🔜 候选：知识库文档列表、黄金集（W8 评估时可能有用） |
| **Prompts** | 用户显式触发 | 参数化的 prompt 模板（含 few-shot），客户端可枚举 | 🔜 候选：CO-STAR 分析模板（prompt 域已有，暴露成本低） |

W6 范围只做 **Tools**（M2 验收门就是"被外部客户端真实调用"），Resources/Prompts 留作后续增量——协议分层设计的红利：三原语各自独立协商，加一个不破已有。

## 4. 生命周期（一次会话的三段式）

```
Client                                Server
  │── initialize ────────────────────▶│  ① 协商：protocolVersion、capabilities、client/server info
  │◀──────────── InitializeResult ────│     双方各自声明支持哪些原语（tools? resources? prompts?）
  │── notifications/initialized ─────▶│  ② 确认：此后才允许业务请求
  │                                   │
  │── tools/list ────────────────────▶│  ③ 业务期：发现 → 调用
  │◀──────────── tools 数组 ──────────│
  │── tools/call {name, arguments} ──▶│
  │◀──────────── CallToolResult ──────│  content[]（text/image/...）+ isError 标志
  │                                   │
  │── （HTTP 无显式关闭；stdio 关流）──▶│  ④ 结束
```

要点：
- **initialize 必须先于一切**，server 在收到 `initialized` 通知前应拒绝业务请求——这是协议状态机，Spring AI starter 会代管，但排查问题时要知道它在哪一层。
- `CallToolResult.isError=true` 表示**工具执行失败**（业务错误），协议错误用 JSON-RPC error（-32601 method not found 等）。两层错误语义分离，映射到我们的 `GlobalExceptionHandler` 时要留心：业务异常应进 `isError`，不该变成 HTTP 500。
- 参数校验靠 JSON Schema（`inputSchema`），server 端仍要再做业务校验——schema 是契约，不是防线。

## 5. Transport：stdio vs Streamable HTTP

规范只有两个标准 transport：

| | stdio | **Streamable HTTP**（2025-03 引入，取代旧 HTTP+SSE） |
|---|---|---|
| 形态 | client 拉起 server 子进程，管道通信 | server 是独立 HTTP 服务，单端点 POST（请求）+ 可选 SSE 流（响应/通知） |
| 部署 | 本机工具（读文件、查本地库） | 远程/常驻服务，天然适配 k8s |
| 会话 | 进程生命周期即会话 | server 返回 `Mcp-Session-Id` 头维持多会话 |
| 旧版 HTTP+SSE | — | ⚠️ 已废弃，新客户端（如 Codex）**不再支持** |

**本项目选型：Streamable HTTP**。理由三条：
1. ai-evolution 本来就是常驻 Spring Boot 服务 + minikube 云原生形态，stdio 子进程模式相悖；
2. 三工具依赖 Qdrant/SiliconFlow 等网络资源，远程调用语义一致；
3. Codex 只支持 stdio + Streamable HTTP，选 HTTP 客户端覆盖面最大。

Spring AI 侧对应 `spring-ai-starter-mcp-server-webmvc`（WebMVC 栈，与现有应用一致；WebFlux 版是 `webflux`，不混用）。

## 6. 与现有工程的映射检查（Step2 设计输入）

- **复用验证点**：三个 `@Tool` 方法在 tool 域，MCP server 只是"换一种暴露方式"。
  如果抽象干净，Step2 应几乎零业务代码——这是对 A10（面向接口）的一次实战检验。
- **新依赖引入新域**：MCP 暴露层属于**接入层**，按 A9 建 `mcp` 子域，
  白名单方向 `mcp → tool`（单向，tool 域不回知 MCP），同步更新 `ArchitectureTest.ALLOWED_DEPENDENCIES`。
- **安全面扩大**：`/ai/*` 是我们自己的 Web UI 在调，MCP 端点会被任意客户端探测。
  W7 安全周的注入攻击实验正好有真实靶子——Step2 先裸通，W7 再加固（在 ADR 里记这个顺序决策）。
- **端点约定**：Spring AI 默认暴露 `/mcp`（可配），与现有 `/ai/*` 命名空间隔离，OpenAPI 契约不覆盖 MCP 端点（协议自带 schema，A7 不适用于协议端点——但要在 README 能力表登记）。

## 7. 关键名词速查

- **Host**：用户直接用的 AI 应用（Claude Desktop、Codex、Cursor）
- **Client**：Host 内部的协议端，与 Server 1:1 会话
- **Server**：能力提供方（我们的 ai-evolution）
- **JSON-RPC 2.0**：消息格式，四种帧——request / response / notification / error
- **Capability negotiation**：initialize 时双方声明支持的原语与子能力（如 tools.listChanged）

---

*精读产出：本笔记即 Step1 交付物。Step2 按第 6 节映射执行。*
