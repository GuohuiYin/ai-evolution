# W29 计划：Web 安全与认证授权业界标准对齐（提案）

> 2026-11-04 预立案（W28 立案当日，二期一页纸排序产物）。主题：安全捆——过滤器链一次到位，
> 不拆散反复触碰同一批测试（O3）。输入：候选总表 #5/#11/#12/#13（均含 2026-11-03 深扫证据）。
> **依赖：W28 收官后开工**；若 W28 任务 1 取证不达标挂账顺延，本主题不变（与召回线正交）。
> 动机原点：Owner 2026-10-31 对现有 authentication/authorization 明确不满，要求对照业界标准做法。

## 输入盘点（现状与业界差距，逐项挂证据）

| 项 | 现状（证据） | 业界标准 |
|---|---|---|
| 认证 | 单 API Key 全权限（`ApiKeyAuthFilter`，X-API-Key 头，常量时间比对 ✅） | OAuth 2.1 + OIDC；用户走授权码+PKCE，服务间走客户端凭证 |
| 授权 | 无角色/scope 概念——一把 key 通吃所有端点 | RBAC 起步，细粒度再 ABAC；Spring Security 授权管理器 + 方法级注解 |
| 令牌生命周期 | 无过期/吊销/轮转 | 短期 access token + refresh 轮转 + 吊销清单 |
| 安全响应头 | 全无（裸 Filter 链） | CSP / X-Content-Type-Options / X-Frame-Options / Referrer-Policy |
| CORS | 零配置（同源现状安全，前后端分离即风险） | deny-by-default + origin 白名单显式声明 |
| SSRF | MCP fetch 可抓任意 URL 含内网（守 robots 未守内网段） | egress 白名单 / RFC1918+loopback 黑名单 |

**适配判断**：现阶段单人本地/演示项目，本周只把接口形态向 OAuth2 对齐（Bearer 头、
标准 401/403、scope 语义），不接 IdP——**IdP 已由 Owner 2026-11-04 裁决立项 W30 自建，
走 Spring Authorization Server（框架级自建，业界认可；不从零手写协议）**，本周的
Bearer/RBAC/scope 对齐正是为 W30 资源服务器改造预留的最小迁移面。

## 任务表（提案）

| # | 任务（验收口径） | 产出 | 状态 |
|---|---|---|---|
| 1 | **认证授权对齐**（候选 #5）：自研过滤器收口进 Spring Security 过滤器链；最小 RBAC——角色表配置化（reader=只读查询 / writer=反馈与对话 / admin=成本与审批管理），端点级角色映射显式声明；Bearer Token 形态（`Authorization: Bearer`）与 X-API-Key 兼容期裁决（双收或切换，取证后定）；401/403 语义分清（认证失败 vs 授权不足）；ADR 留痕（A4，why-not 含：本周不接 IdP——中心 W30 才建，本周对齐形态是为其预留迁移面） | 重构 + ADR + 全量安全测试绿 | ☐ |
| 2 | **安全响应头套件**（候选 #11）：CSP（对话页自托管资源白名单）+ X-Content-Type-Options + X-Frame-Options + Referrer-Policy，走 Spring Security 头套件而非手写 | 配置 + 头断言测试 | ☐ |
| 3 | **CORS 显式策略**（候选 #12）：deny-by-default + origin 白名单配置化（本地开发 origin 入 `.env.example`），预检请求行为测试锁定 | 配置 + 测试 | ☐ |
| 4 | **SSRF egress 控制**（候选 #13）：MCP fetch 目标 URL 校验——RFC1918/loopback/link-local 黑名单拒连（配置化例外清单），或反向白名单（取证裁决）；工具层单测 + 红队基线复测（W7 防线不回归） | 修复 + 测试 + 红队复测记录 | ☐ |

## 退出条件（O8）

四任务交付 + W7 红队基线复测不回归 + ArchUnit/全部单测绿；RBAC 角色映射表作为安全
审计面入能力地图「鉴权/限流」行收口。任一任务取证后判定不适合现阶段（如 Bearer 切换
收益不抵迁移面），按先例降级为挂账并登记证据——不硬上。

## Owner 裁决

（待 W28 收官时复核确认本周开工；排序已于 2026-11-04 二期一页纸拍板）
