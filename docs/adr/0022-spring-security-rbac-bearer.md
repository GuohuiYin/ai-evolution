# ADR-0022：认证授权收口进 Spring Security 链（RBAC 三角色 + Bearer 正史）

- 日期：2026-11-05（W29 #1）
- 状态：已采纳
- 关联：ADR-0013（安全配置显式化）、[w29 设计草案](../security/w29-auth-alignment-design.md)、W30 统一认证授权中心计划

## 背景

W12 #2 落地的自研 `ApiKeyAuthFilter` 是「单 key 全权限」：无角色/scope 概念、401/403 不分、
游离于 Spring Security 之外（裸 servlet Filter 链）。Owner 2026-10-31 明确要求对照业界
标准做法；W30 已定调自建认证中心（Spring Authorization Server），本周改造为其预留迁移面。

## 决策

1. **收口**：认证授权职责迁入 Spring Security 过滤器链——`ApiCredentialAuthenticationFilter`
   （认证：Bearer/X-API-Key → 身份+角色）+ 端点角色映射（授权）+ 401/403 统一 ProblemDetail
   出口。自研过滤器删除，常量时间比对与 ProblemDetail 语义保留平移。
2. **最小 RBAC**：三角色 reader（只读查询）/ writer（对话与反馈）/ admin（成本与审批管理），
   端点映射配置化（`ai.security.endpoint-roles`，空表 fail-fast）；角色层级
   `admin ⊇ writer ⊇ reader`（`RoleHierarchy`），授权规则只声明最低角色。
3. **Bearer 正史 + X-API-Key 兼容期双收**（Owner 裁决 A）：`Authorization: Bearer` 为契约正史
   （OpenAPI 双 scheme 表达二选一），X-API-Key 标 deprecated、首次使用 WARN 留痕，W30 接
   JWT 时退役。key 身份角色由 `ai.security.key-roles` 配置（单 Owner 时代 = admin）。
4. **CSRF 关闭**：纯无状态 API、无 Cookie 会话，CSRF 无从附着。安全头套件保持关闭，
   W29 #2 逐头启用并配测试，不与收口混在一个变更里。
5. **安全链只管 REQUEST/ERROR 转发**（`spring.security.filter.dispatcher-types`）：MCP
   Streamable HTTP 与 SSE 异步二次入链时 SecurityContext 为空会误抛 403（响应已提交后炸流，
   实测 `McpServerIT` "response is already committed"）——初访已完成鉴权，异步转发不重复鉴权。

## 备选与 why-not

- **继续自研过滤器**：短期省事，但角色/层级/异常翻译/顺序全要自己重造 Spring Security 已有的
  轮子，且与 W30 资源服务器改造（`oauth2ResourceServer`）路径不通——自研每多一天，迁移面大一分。
- **本周直接接 IdP / Keycloak**：中心 W30 才建（Owner 裁决学习目标优先，自建 Spring
  Authorization Server）；本周对齐的是「形态」（Bearer/RBAC/scope 语义），不是「中心」。
- **X-API-Key 一步切换退役（候选 B）**：仓库内发送点虽少，但 MCP 客户端配置在仓库外、
  依赖 Owner 手工步骤，一步切换留下「代码已切、本地配置未切」的窗口期；双收把切换主动权
  留给 Owner，兼容期随 W30 自然结束。

## 后果

- 迁移面兑现：W30 只换「凭证解释」（opaque key → JWT/JWKS 验签）与「角色来源」（配置表 →
  scope），Bearer 头与端点映射不动——「身份从哪来」与「身份能干什么」已切开。
- 兼容期代价：双通道并存期间审计面 ×2（两种头都有效），由 WARN 留痕 + W30 明确退役日收口。
- ASYNC 转发不重复鉴权是安全边界的有意取舍：初访鉴权后流式响应全程受信，适用于本系统
  单实例形态；多副本/网关前移时重评。
- 401 现附 `WWW-Authenticate: Bearer`（RFC 9110），客户端可自引导。
