# W12 计划：MCP Client + 鉴权与限流（出入双向凭证主题周）

> 对应 M3 阶段索引 W12 行。主题：协议的另一面 + 服务的安全门。
> 一周讲透"凭证"双向：我们的 Agent 持证调别人（MCP Client auth），别人持证调我们（API Key 鉴权 + 限流）。

## 背景（为什么做）

- W6 我们实现了 MCP Server（别人调我们）；MCP Client 是协议栈的另一半——Agent 以标准协议
  调用外部工具服务取数，是研究 Loop"向外取数"的能力底座
- "标准答案"口径扫描（2026-09-10）：全端点零鉴权、无限流——🔴 硬伤，安全章节缺页

## 任务分解

| # | 任务 | 验收条件 |
|---|---|---|
| 1 | **MCP Client 接入**（TDD）：Spring AI MCP Client 连接一个真实外部 MCP 服务（候选：官方 fetch/time 等公开服务，开工时选型）；封装为 `ExternalToolClient` 接口（IOP），挂入 Agent 工具面板 | 我们的 Agent 通过 MCP 协议真实调通外部服务一次，日志可见调用链（traceId 串联）；单测 mock 协议层 |
| 2 | **API Key 鉴权**：`/ai/**` 与 `/mcp` 加 API Key 校验（Filter 实现，key 走环境变量配置 A5）；Swagger UI/对话页/actuator 健康探针的放行策略显式决策 | 无 key 401；合法 key 正常；放行清单测试锁定；OpenAPI 标注安全方案（A7） |
| 3 | **频率限制**：简单令牌桶/固定窗口（每 key 每分钟 N 次，配置化 A11） | 超限 429 + ProblemDetail（B3）；测试锁定阈值行为 |
| 4 | **模型自动降级裁决**（能力地图挂账）：DeepSeek 故障自动切 Qwen 是否本周做 | 裁决记录：做 → TDD 落地（超时/5xx 触发切换 + 告警日志）；挂 W14+ → 记录理由与触发条件 |

## 明确不做

- 不做 OAuth2/JWT 完整授权体系（学习成本与当前收益不匹配，W14+ 候选）
- 不做分布式限流（单实例内存实现；多副本场景等 L2 持久化一起谈）
- 不动 ReAct Loop 内部结构（W10/W13 的地盘）

## 顺序与节奏

1 → 2 → 3 → 4。每天 1 项，逐项 review。任务 4 是裁决项，产出可能是 ADR 而非代码。

## 开工口令

读 AGENTS.md + 本文件 + docs/capability-map.md + docs/plans/m3/README.md，继续 W12 任务 #1。
