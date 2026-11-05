# W29 红队基线复测记录（2026-11-05）

> 方法同 W7（实弹打活实例）：本地 18080 以 W29 新代码重启，逐项 curl 攻击/验证。
> 复测范围聚焦**本周改动面**（HTTP 安全层）；prompt 注入类用例（A1-A4/I1-I2）防线在
> prompt 资产与模型自觉层，本周 prompt 零变更，防线位置未变，不重复烧模型预算。

## HTTP 安全层逐项

| # | 用例 | 手法 | 结果 | 判定 |
|---|---|---|---|---|
| R1 | 无凭证调业务端点 | `POST /ai/chat` 无头 | 401 ProblemDetail + `WWW-Authenticate: Bearer`，**无 JSESSIONID**（无状态姿态） | ✅ 守住 |
| R2 | 错凭证 | `Authorization: Bearer wrong` | 401 | ✅ 守住 |
| R3 | 正史通道 | `Bearer <key>` → `GET /ai/cost/summary` | 200 | ✅ 正史可用 |
| R4 | 兼容通道 | `X-API-Key: <key>` → 同上 | 200（首次使用 WARN 留痕，见应用日志） | ✅ 裁决 A 兑现 |
| R5 | 响应头套件 | `GET /actuator/health` 抓响应头 | nosniff + `X-Frame-Options: DENY` + CSP `default-src 'self'…` + `Referrer-Policy: no-referrer` | ✅ 四头齐 |
| R6 | CORS 未登记 origin 预检 | `OPTIONS /ai/chat` Origin=evil.example | 403，无 `Access-Control-Allow-Origin`（deny-by-default） | ✅ 守住 |
| R7 | 超大请求体 | 40KB JSON（上限 32KB） | 413（W28 #3 防线在 Security 链改造后不回归） | ✅ 守住 |
| R8 | MCP 无会话无凭证 | `POST /mcp` initialize 无头 | 401（不再裸奔，也不泄堆栈——认证先于协议解析） | ✅ 守住 |
| R9 | MCP 持证未知方法（W7 M1' 复测） | 握手取 session → `no/suchMethod` | 干净 `-32601 Method not found`（经 SSE 流送达；curl 需限时截流属协议语义） | ✅ 不回归 |

## SSRF 防线（W29 #4 新增面）

活实例复测需经模型 Loop 触发 fetch 工具（成本高），由单测锁定替代：
`EgressUrlGuardTest` 9 项（127.0.0.1 / 10.x / 172.16.x / 192.168.x / 169.254.169.254 云元数据端点 /
0.0.0.0 / 解析到内网的主机名 / 混合解析 / file·ftp 协议 / fail-closed / 例外清单）+
`McpExternalToolClientTest.privateTargetIsBlockedBeforeAnyRemoteCall`（拦截先于协议往返，
被拒 URL 零出向调用）。

**如实声明的边界**：守卫的 DNS 解析与 fetch 服务的真实解析存在 TOCTOU 窗口（DNS rebinding
可绕过）；单 Owner 本地场景接受，生产级处置是 egress 代理统一出向。边界写在
`EgressUrlGuard` 类注释，不夸大防线强度。

## 复测中捕获的新缺陷（当场修复）

Spring Security 默认会话策略在 401 时建会话发 JSESSIONID Cookie——与「无 Cookie 会话」的
CSRF 关闭论证自相矛盾。修复：显式 `SessionCreationPolicy.STATELESS`，测试断言 401 响应
无 JSESSIONID（`SecurityChainTest`）。**实弹复测的价值兑现：静态审查没抓到，活实例抓到了。**
