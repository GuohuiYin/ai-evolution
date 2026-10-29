# 横切视图：一次请求穿过的关注点分层

> 补充视图（非 C4 静态层级）：任何一个 HTTP 请求从进来到出去，依次穿过哪些横切层、
> 每层由哪个组件把守、在哪留痕。读法：**自上而下是请求流向，右侧是每层挂载的横切关注点**。
> 截止 2026-10-29（W25 #1b Phoenix 落地）。

```mermaid
flowchart TB
  REQ["HTTP 请求进入<br/>（X-API-Key / traceId 或无）"]

  subgraph L1["① 接入层——挡在门外"]
    SEC["ApiKeyAuthFilter<br/>鉴权：空 key 启动即 fail-fast"]
    RATE["固定窗口限流<br/>60 次/分钟/身份"]
    TID["TraceIdFilter<br/>生成/透传 traceId 写 MDC"]
  end

  subgraph L2["② 对话编排层——分流与装配"]
    ROUTER["ChatRouter 规则路由<br/>RAG 管道 / Agent 管道"]
    ADV["Advisor 链<br/>记忆读取（窗口 20）· prompt 模板渲染 · prompt/response DEBUG 留痕"]
  end

  subgraph L3["③ 领域层——业务计算"]
    RAG["RAG 检索生成<br/>改写→两阶段检索→多样性配额→生成→引用校验"]
    LOOP["研究循环<br/>plan→build→review→HITL（Temporal 编排）"]
  end

  subgraph L4["④ 资源层——出向调用全部有哨兵"]
    FO["FailoverChatModel<br/>DeepSeek 故障→切 Qwen；重试压 2 次快速失败"]
    AUDIT["ToolAuditAspect<br/>工具调用审计 + 入参 ≤2000 字符"]
    MCPW["MCP 白名单派发<br/>ExternalToolClient（不自动注册全局）"]
  end

  subgraph L5["⑤ 合规与出口"]
    RED["RedLineGuard 红线校验（告警不拦截）+ 免责声明"]
    SSE["SSE 流式回推：过程轨迹 + 终答"]
  end

  REQ --> SEC --> RATE --> TID --> ROUTER --> ADV
  ADV --> RAG
  ADV --> LOOP
  RAG --> FO
  LOOP --> AUDIT
  LOOP --> MCPW
  RAG --> RED
  LOOP --> RED
  RED --> SSE

  subgraph TRACE["可观测留痕（每层都在写）"]
    LOG["控制台日志带 traceId"]
    EVENT["Temporal 事件历史<br/>（loop 域）"]
    METRIC["metrics 端点<br/>failover 计数器等"]
    OTEL["OTel trace 上报 Phoenix（W25 落地）<br/>gen_ai.* + app.trace_id 同键互查"]
  end

  TID -.-> LOG
  LOOP -.-> EVENT
  FO -.-> METRIC
  LOG -.-> OTEL
```

## 每层一句话

| 层 | 把守者 | 记忆点 |
|---|---|---|
| ① 接入 | 鉴权/限流/traceId | 鉴权没有「忘记开」的静默中间态——空 key 直接启动失败（ADR-0013） |
| ② 编排 | ChatRouter + Advisor 链 | 记忆定点挂载：只有对话域挂，eval/judge 不共享（防污染） |
| ③ 领域 | RAG / Loop | 业务层不含安全与留痕代码——全在横切层 |
| ④ 资源 | Failover / 审计 / 白名单 | 所有出向调用都有哨兵：模型有 failover、工具有审计、MCP 有白名单 |
| ⑤ 出口 | 红线 + SSE | 「看得见先于拦得住」（W7）：第一版红线只告警 |

## 横切关注点的归属速查

- **安全**：① 鉴权限流 + ④ 工具边界 + ⑤ 内容合规（三层纵深）
- **可观测**：traceId 四面同键（日志/Temporal UI/反馈快照/W25 OTel 链路）
- **成本**：token 账（W7 TokenUsageAdvisor → W8 一页账 → W25 JSONL 台账 + 仪表盘端点 + 预算熔断）
- **韧性**：failover 快速失败 + Temporal 崩溃重放 + manifest 损坏自愈
