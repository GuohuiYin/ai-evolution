# ADR-0023：ai-evolution 直切 JWT 资源服务器（API Key 双通道同批退役）

- 日期：2026-11-06（W30 #2）
- 状态：已采纳
- 关联：ADR-0022（收口 Spring Security 链，本篇兑现其迁移面承诺）、[w30 设计草案](../security/w30-resource-server-design.md)、W30 统一认证授权中心计划

## 背景

W30 #1 已建成统一认证中心（auth-center，Spring Authorization Server）：授权码+PKCE（人）与
客户端凭证（机）双通道签发 RS256 JWT，JWKS 公钥端点就绪。主工程侧，W29 的
`ApiCredentialAuthenticationFilter`（opaque Bearer + X-API-Key 双通道、共享密钥常量时间比对）
已完成历史使命——ADR-0022 的迁移面承诺即「W30 只换凭证解释与角色来源」。

## 决策

1. **直切（Owner 裁决）**：主工程只认 auth-center 签发的 JWT，opaque Bearer 与 X-API-Key
   **同批退役，不留兼容期**。对话页/脚本的断供窗口由 Owner 接受（对话页过渡姿势：curl 向
   认证中心 client_credentials 换 token 手工粘贴，正式登录流归 #3）。
2. **验签**：`spring-boot-starter-oauth2-resource-server`，仅凭 JWKS 公钥验签
   （`spring.security.oauth2.resourceserver.jwt.jwk-set-uri`，env `AUTH_CENTER_JWKS_URI`）——
   主工程不再持有任何共享密钥，认证逻辑从业务系统彻底剥离。
3. **角色来源**：`JwtRoleConverter` 把人通道 `role` 声明与机器通道 `scope` 声明（空格分隔）
   统一折成 `ROLE_*`——W29 端点角色表与 `RoleHierarchy`（admin⊇writer⊇reader）零改动。
   两声明皆缺 = 零角色 = 403（授权失败），不混淆为 401（认证失败）。
4. **401/403 同形态**：ProblemDetail 处理器同时挂全局 exceptionHandling 与资源服务器入口点——
   非法 JWT 的 401 与缺凭证、角色不足的拒绝保持同一契约形态。
5. **限流身份**：改按 JWT `sub` 声明分桶（只摘 payload 不验签——分桶不是安全决策，
   伪造 sub 最坏替别人吃额度，不放大自身额度；验签在资源服务器链完成）。
6. **测试真验签不打桩**：`TestJwtSupport` 进程内 RSA 密钥对 + JDK 内嵌 JWKS 端点
   （随机端口），链级测试经 `@DynamicPropertySource` 指向它——验签路径全真。

## 备选与 why-not

- **保留兼容期（opaque/JWT 双收）**：W29 对 X-API-Key 用双收是因为发送点在仓库外（MCP 客户端
  配置靠 Owner 手工）；本次发送点全在仓库内（对话页/脚本同批改），双收只留攻击面不留收益。
- **自研 JWT 校验过滤器**：重造 JWKS 拉取/缓存/验签/过期判断的轮子，安全铁律不做；
  Spring 资源服务器是框架级标准答案（W29 收口的同一逻辑延伸）。
- **角色继续走配置表**：单 key 时代的权宜；JWT 声明自带角色后，配置表与 token 会双源失真。

## 后果

- **断供窗口**：直切当刻起，任何持旧 key 的调用方（含 Owner 本地脚本、对话页）立即 401，
  直到换 JWT——Owner 已裁决接受；`.env` 的 `AI_EVOLUTION_API_KEY` 同步退役（`.env.example` 已清）。
- **启动不拉取 JWKS**：`NimbusJwtDecoder` 首次验签时才取并缓存——auth-center 未起时主工程
  照常启动，仅在持证请求到达时 401（无中心可验）；部署编排上两者无启动顺序依赖。
- **密钥轮转**：当前 auth-center 密钥对为启动生成（重启即换钥，旧 token 全失效）——
  初始密钥管理与轮转规程归 W30 #4，本篇不展开。
- **切片影响**：`spring-boot-starter-oauth2-resource-server` 把资源服务器自动配置带进
  `@WebMvcTest` 切片（需完整 HttpSecurity 装配，切片不具备），6 个切片显式
  `excludeAutoConfiguration` 排除——切片本就不测安全链，链级行为由 SecurityChain*/McpServerIT 锁定。
