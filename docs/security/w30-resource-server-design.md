# W30 #2 设计草案：ai-evolution 改资源服务器（JWT 验签接 W29 Bearer 形态）

> 2026-11-06 起草（W30 #2 第一步：取证 + 设计先行，行为零变化）。
> 裁决点（§二 双轨 vs 直切）请 Owner 裁决后回填「裁决记录」段。

## 一、取证记录（2026-11-06）

| 取证项 | 结果 |
|---|---|
| 资源服务器 starter | `org.springframework.boot:spring-boot-starter-security-oauth2-resource-server` **4.1.1 Central 核实存在**，BOM 托管（Boot 4 官方栈同 SAS 并入姿势） |
| 现状认证实现 | `ApiCredentialAuthenticationFilter`（Bearer/X-API-Key 双收、常量时间比对、只认证不授权）+ `SecurityConfiguration`（角色层级 + 端点角色表配置化 + 401/403 ProblemDetail） |
| W29 预留迁移面 | Bearer 头不变，只换凭证解释（opaque→JWT）；端点角色表不动，只改角色来源（配置表→JWT 声明）——[w29-auth-alignment-design](w29-auth-alignment-design.md) §W30 迁移面 |
| X-API-Key 退役口径 | ADR-0022 已定：**W30 接 JWT 时随 opaque 实现一并退役**——本任务执行既定裁决，不再新开裁决 |
| 限流身份 | `RateLimitFilter` 排在鉴权前，身份=原始头值（Bearer 优先），缺失退化 IP |
| 调用方盘点（断供面） | 对话页 3 处 fetch（#3 才接登录流）、`scripts/` 探针、eval runner、MCP 客户端（客户端凭证流取 token） |

## 二、裁决点：opaque Bearer 直切 vs 双轨过渡

X-API-Key 退役无争议（ADR-0022 既定）。悬而未决的是 **opaque Bearer 的退役时点**：

- **候选 A 直切**：#2 落地即只认 JWT。代价：对话页/脚本在 #3（前端登录流）完成前全部断供，
  本地开发断档一周中几天；MCP 客户端也需同步改配置（仓库外手工步骤）
- **候选 B 双轨过渡（倾向）**：Bearer 头按形态分发——三段式 JWT（含两个 `.`）→ JWKS 验签；
  否则 → opaque key 比对（现有过滤器保留）。opaque 通道每次使用 WARN 留痕（有声化），
  **#3 完成当日 opaque 退役**（显式收口 commit，登记本 ADR 链）。对话页不断档，
  迁移主动权在 Owner 手里（与 W29 裁决 A 的气质一致）

## 三、设计（按 Owner 裁决 A 直切，2026-11-06）

- **依赖**：`spring-boot-starter-security-oauth2-resource-server`（BOM 托管）
- **配置**：`spring.security.oauth2.resourceserver.jwt.jwk-set-uri` env 化
  （默认 `http://localhost:19000/oauth2/jwks`）
- **角色映射**：`JwtAuthenticationConverter` 自定义——`role` 声明（人通道，用户表单一事实源）
  与 `scope` 声明（机器通道）统一映射 `ROLE_*`；W29 端点角色表 + RoleHierarchy 零改动
- **直切**：`ApiCredentialAuthenticationFilter` 整类删除（opaque Bearer 与 X-API-Key 同批退役，
  后者为 ADR-0022 既定口径）；Bearer 一律走资源服务器 `BearerTokenAuthenticationFilter` 验签
- **限流身份**：Bearer 为 JWT 形态时取 `sub` 声明分桶（仅分桶不验签——限流不是安全决策，
  验签在链上）；否则沿用原始头值/IP 退化
- **OpenAPI**：bearer scheme 标注 `bearerFormat: JWT`；apiKey scheme 删除
- **断供处置**：对话页 3 处 fetch 在 #3 前 401（Owner 接受的窗口期）；`scripts/` 内调用方
  迁移为客户端凭证流取 token；MCP 客户端配置在仓库外，Owner 手工迁移（留迁移说明）
- **MCP/SSE**：`dispatcher-types: request, error` 配置不动（W29 异步二次入链教训在案）

## 四、测试策略（真验签，不打桩）

- 测试基建：测试内生成 RSA 密钥对 + 微型 JWKS JSON 端点（`com.sun.net.httpserver` 内嵌），
  `jwk-set-uri` 指向它——端到端真验签，不 mock JwtDecoder
- 行为锁定：有效 JWT（role/scope 两型）→ 200 + 正确角色授权；过期/错签名/错 iss → 401；
  无凭证 → 401 ProblemDetail（W29 语义不回归）；角色不足 → 403
- 双轨期：opaque Bearer 仍 200（WARN 留痕）；X-API-Key → 401（退役兑现）
- 现有 `SecurityChainTest`/`SecurityChainRbacTest` 语义平移为 JWT 版，头套件/413/429 断言不动

## 五、分步拆解（O3）

1. 取证 + 设计草案（本步，零行为变化）
2. JWT 通道上线：依赖 + 配置 + Converter + 测试基建 + 行为锁定（双轨并行，opaque 仍在）
3. X-API-Key 退役：通道删除 + OpenAPI 收口 + 调用方清点留痕 + 计划打勾

## 裁决记录

**Owner 裁决（2026-11-06）：候选 A 直切**——#2 落地即只认 JWT，opaque Bearer 与 X-API-Key
同批退役；对话页/脚本在 #3 前的断供窗口 Owner 明确接受（对话页体验让位架构收敛，
#3 登录流补回）；MCP 客户端配置（仓库外）由 Owner 按迁移说明手工切换到客户端凭证流。
