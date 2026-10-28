# C4 L3：组件图 · infra 横切域

> 本张聚焦 **横切关注点**——不属于任何单一业务域、但每个请求都穿过的组件：
> 安全、记忆、模型韧性、审计、技能、合规。截止 2026-10-28（W24 收官态）。

```mermaid
C4Component
  title 组件图：infra/横切域（安全·记忆·韧性·审计·技能·合规）

  Container_Boundary(app, "ai-evolution 主进程") {
    Component(sec, "SecurityConfiguration / ApiKeyAuthFilter", "Spring Security", "X-API-Key 强制鉴权，空值启动 fail-fast；放行清单测试锁定（ADR-0013）")
    Component(ratelimit, "固定窗口限流", "Filter", "60 次/分钟/身份（API Key，无凭证按 IP）；单实例内存实现，多副本挂账")
    Component(trace, "TraceIdFilter", "Filter + MDC", "traceId 四面同键：日志/Temporal UI/反馈快照/（W25）OTel 链路")
    Component(memorycfg, "ConversationMemoryConfig", "@Configuration", "ChatMemory 双仓储旗标：inmemory（缺省）/ jdbc-MySQL（L2）；窗口 20 条；schema 脚本按数据库产品名自动选取，幂等重放")
    Component(failover, "FailoverChatModel", "ChatModel 装饰器", "DeepSeek 超时/5xx → 切 Qwen（SiliconFlow）；内部重试压到 2 次快速失败再切换（W12 故障注入实测定参）")
    Component(audit, "ToolAuditAspect", "AOP 切面", "工具调用审计（非入侵埋点，ADR-0008）；入参上限 2000 字符防成本攻击")
    Component(redline, "RedLineGuard / Disclaimers", "compliance 包", "红线校验+免责声明；服务端第一版只告警不拦截（ADR-0011）")
    Component(skillreg, "SkillRegistry", "skill 包", "启动扫描 SKILL.md（YAML 头 name/description/triggers）；注入模式 off/full/progressive 三态——实测未达 20% 守线，默认 off")
    Component(promptlog, "SimpleLoggerAdvisor 配置", "Spring AI", "prompt/response DEBUG 留痕；生产必须关（金融语料敏感）")
    Component(mcpcfg, "McpClientToolConfiguration + McpErrorSanitizingConfig", "mcp 包", "MCP 工具不自动注册全局——白名单派发（ExternalToolClient）；错误脱敏防内部细节外泄")
  }

  Component_Ext(chatclients, "ChatClient 各通路", "chat/loop/eval 域", "RAG/Agent/管线/judge 各自独立实例")
  Component_Ext(tools, "StockDataTools / AnnouncementTools / ExternalToolClient", "tool 包", "@Tool 与 MCP 白名单工具")
  System_Ext(deepseek, "DeepSeek API")
  System_Ext(qwen, "Qwen（SiliconFlow 托管）", "降级臂")
  System_Ext(mysql, "MySQL")
  System_Ext(mcpfetch, "MCP fetch 服务")

  Rel(chatclients, failover, "全部模型调用经此")
  Rel(failover, deepseek, "主", "HTTPS")
  Rel(failover, qwen, "备（故障时）", "HTTPS")
  Rel(memorycfg, mysql, "jdbc 旗标时", "JDBC")
  Rel(tools, audit, "调用被切面记录")
  Rel(mcpcfg, mcpfetch, "白名单派发 fetchWebPage", "MCP stdio/HTTP")
```

## 读图要点

- **failover 是所有模型调用的唯一咽喉**：重试压到 2 次（库默认 10 次会把供应商故障
  拖成十分钟级挂起——W12 故障注入实测抓获），快速失败后切备用臂；降级期延迟
  30-60x，靠告警日志+计数器可见。
- **记忆配置的刻意手工**：DataSource 自动配置显式排除，jdbc 模式手工装配——
  保证 inmemory 缺省态零 DataSource 副作用，换库只动配置不动代码。
- **安全三层的分工**：入口鉴权限流（sec/ratelimit）→ 工具边界（audit/白名单/入参上限）
  → 内容合规（redline 告警）。「看得见先于拦得住」（W7 原则）。
- **Skill 机制是「止步」样板**：机制造好了、双臂实测 2.0%/9.7% 均未达 20% 守线，
  默认 off 登记挂账——能力具备但不启用，等重评触发条件（W25 #3 模型路由可能改变格局）。
