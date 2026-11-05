# W29 任务 1 设计草案：认证授权对齐（Spring Security 收口 + 最小 RBAC + Bearer 形态）

> 2026-11-05 起草（W29 #1 第一步：依赖引入 + 设计先行，行为零变化）。
> 取证裁决点（Bearer 双收 vs 切换）在本文成文后执行，结论回填本文「裁决记录」段。

## 目标与非目标

- **目标**：自研 `ApiKeyAuthFilter` 的鉴权职责收口进 Spring Security 过滤器链；最小 RBAC
  三角色；`Authorization: Bearer` 形态；401/403 语义分清；为 W30 资源服务器改造预留最小迁移面
- **非目标**：不接 IdP（W30 的事）；不动会话/业务逻辑；不改 MCP 协议层语义

## 角色表（配置化，`ai.security.roles`）

| 角色 | 语义 | 端点映射（按 2026-11-05 端点盘点） |
|---|---|---|
| reader | 只读查询 | `GET /api/approvals/{id}`、`GET /ai/cost/summary`、`GET /ai/cost/budget` |
| writer | 对话与反馈 | `POST /ai/chat`、`POST /ai/chat/stream`、`POST /ai/analyze`、`POST /ai/agent`、`POST /ai/feedback` |
| admin | 成本与审批管理 | `POST /ai/cost/budget`（调额）、`POST /api/approvals/{id}`（放行/驳回） |
| （公开） | 无凭证放行 | 对话页静态资源、Swagger UI/api-docs（本地）、`/actuator/health`（K8s 探针）——沿用现有 `SecurityAllowlist` 口径 |
| MCP | 机器通道 | `POST /mcp`：现阶段等同 writer；W30 走客户端凭证流程后独立 |

映射写成配置而非注解硬编码（A11 第二问：这是策略不是常量）。

## 401 vs 403（本周起分清）

| 情形 | 现状 | 目标 |
|---|---|---|
| 无凭证 / 凭证非法 | 401 ✅ | 401（认证失败，`WWW-Authenticate: Bearer` 头） |
| 凭证合法但角色不足 | （无此概念，单 key 通吃） | 403（授权不足） |

## Bearer 双收 vs 切换（取证裁决点）

- **取证问题**：当前谁在发 `X-API-Key`？——对话页 fetch、`scripts/` 探针、eval runner、
  MCP 客户端（Codex/Inspector 配置）、k8s 探针（走放行清单，不带凭证）。
- **候选 A 双收**：`Authorization: Bearer <key>` 与 `X-API-Key: <key>` 映射同一身份
  （现阶段单 Owner，身份即 admin）。Bearer 成为正史，X-API-Key 标 deprecated 留兼容期。
- **候选 B 直接切换**：全仓调用点一次改完，X-API-Key 退役。
- **裁决口径**：清点 `X-API-Key` 全部发送点——≤5 处且全在自己手里则倾向 B（一步到位，
  不留双通道审计面）；有外部/难改发送点则 A。结论回填下段。

## 裁决记录

**取证结果（2026-11-05，全仓清点 `X-API-Key` 发送点）**：

| 发送点 | 位置 | 改动成本 |
|---|---|---|
| 对话页 | `static/index.html`（3 处 fetch + 1 处 prompt 录入） | 低（仓库内） |
| 流量仿真脚本 | `scripts/w26_traffic_simulator.py` | 低（仓库内） |
| OpenAPI 安全声明 | `OpenApiSecurityConfiguration`（Authorize 按钮 scheme） | 低（仓库内） |
| MCP 客户端 | Codex/Inspector 本地配置（**仓库外**，Owner 手改） | 中（人工步骤） |
| 测试 | `ApiKeyAuthFilterTest` / `McpServerIT` 等 | 随实现平移 |

意外发现：`scripts/judge_rescore_w24_2.py` 早已在用 `Authorization: Bearer` 头——
服务端当时只认 X-API-Key，该脚本实际未携带有效凭证（走的是哪条路径待查，不影响裁决）。

**Owner 裁决（2026-11-05）：候选 A 双收 + 兼容期**——仓库内 3 类发送点虽可一次改完
（倾向 B 的条件基本满足），但 MCP 客户端配置在仓库外、改动依赖 Owner 手工步骤，一步切换
会留下「代码已切、本地配置未切」的窗口期；双收把切换窗口的主动权留给 Owner，W30 接 JWT 时
X-API-Key 随 Bearer opaque 实现一并退役，兼容期自然结束。落地见 [ADR-0022](../adr/0022-spring-security-rbac-bearer.md)。

## 落链形态与顺序

```
TraceId → 请求体413 → 限流429（三道裸过滤器保持原位，HIGHEST_PRECEDENCE+0..2）
→ Spring Security 链（认证：Bearer/X-API-Key → 身份+角色；授权：端点→角色映射，403）
→ Controller
```

- `ApiKeyAuthFilter` 退役：常量时间比对、ProblemDetail 401 格式保留语义，搬进链内
  AuthenticationEntryPoint / AccessDeniedHandler（401/403 仍走 RFC 7807，B3 不破）
- 放行清单（`SecurityAllowlist`）原样迁移为链内 permitAll 规则
- 过渡链（全放行 bean）届时删除

## W30 迁移面（本设计的约束）

- Bearer 头不变，W30 只换「凭证解释」：opaque key → JWT（JWKS 验签）
- 角色表/端点映射不动，W30 只改角色来源：配置表 → JWT scope
- 即：**本周把「身份从哪来」与「身份能干什么」切开**，W30 只动前者

## 测试策略

- 401/403 语义测试锁定（无 key / 错 key / 角色不足三案）
- 角色映射表单测（每端点归到预期角色）
- 现有 `ApiKeyAuthFilterTest` 语义平移；W7 红队基线周末复测不回归
- ArchUnit：安全规则集中在 infra 域，Controller 不出现角色注解散点
