# A2A 协议精读笔记（W23 #3a，裁决③「会用」档）

> 目标：不写代码，先读懂协议，回答三个问题——A2A 解决什么、与 MCP 的分工边界在哪、
> 落到本项目（个股研究助手）的接入点是什么。
> 依据：A2A 官方规范 v1.0（2026-03-12 发布，v1.0.1 补丁 2026-05-28，
> [a2a-protocol.org](https://a2a-protocol.org)）；治理现状见 [AAIF 公告（2026-08）](https://waxell.ai/blog/a2a-agentic-ai-foundation-agent-identity)。
> 对照笔记：[mcp-protocol](mcp-protocol.md)（W6-Step1）。

## 一、A2A 解决什么：Agent 互操作的 N² 问题

现代企业里跑的不是一个 Agent，而是一群出自不同厂商、不同框架、不同团队的 Agent。
两两互通 historically 要写定制连接代码，配对数随 Agent 数平方增长——这就是 Agent
互操作问题。A2A（Agent2Agent）给出通用契约：**发现（Agent Card）→ 委派（Task）→
回传（Artifact）**，双方不用共享内存、运行时或厂商。

一句话分工：**MCP 是 Agent 够工具的协议（向内），A2A 是 Agent 够同事的协议（向外）**。

治理时间线：Google 2025-04 发布（50+ 伙伴）→ 2025-06 捐赠 Linux Foundation →
IBM ACP 2025-08 并入（协议层收敛而非分裂）→ 2026-03 v1.0 首个生产就绪版 →
2026-08 进入 Agentic AI Foundation（AAIF，与 MCP 同屋治理，150+ 组织背书）。

## 二、五个核心对象与任务生命周期

| 对象 | 职责 |
|---|---|
| Agent Card | 发现机制的全部：JSON 自述身份/技能/端点/认证方式；v1.0 起支持 JWS 签名（RFC 7515）防卡片篡改 |
| Task | 有状态的工作单元，生命周期 8 态（含 submitted/working/**input-required**/completed/failed/canceled 等） |
| Message | 往来对话轮次（role + parts） |
| Part | 消息/工件的内容单元（文本/文件/结构化数据） |
| Artifact | Task 的产出物（结果文件、报告等） |

三个值得记住的设计：

1. **input-required 是一等状态**——远端 Agent 可以把 Task 挂起等调用方（或人）补输入。
   这和我们 W23 #1 的 HITL 挂起（`awaiting-approval`）是同一语义在不同层的显形：
   我们的挂起在 Temporal workflow 内，A2A 把「挂起等输入」标准化进了协议状态机。
2. **不透明执行（opaque execution）**：双方只交换声明的能力与消息，**不交换内部计划、
   推理过程或记忆**——各家 Agent 的内部实现（ReAct/三角色/任何编排）保持黑盒。
3. **长任务友好**：SSE 流式推送状态更新 + webhook 推送通知，Task 可以跨小时/天存活。

## 三、传输与认证

- 三种传输绑定：JSON-RPC 2.0 / gRPC / HTTP+JSON(REST)——无私有线路格式，
  任何 HTTP 运行时都可实现；跨云调用就是普通 HTTPS，现有网络控制（私有端点/
  VPC 对等/出口规则）原样适用
- 认证：每个 Agent 在 Agent Card 里声明自己的方案（v1.0 定义 6 种，含 OAuth 2.0
  Device Code + PKCE）——**认证是声明式的发现内容**，不是事后补丁
- 风险面（部署方责任）：Agent 仿冒、卡片篡改（签名卡可防）、webhook SSRF、
  重放攻击——签名卡解决「卡片是谁发的」，不解决「该不该委派给它」

## 四、A2A vs MCP 分工边界（对照自家实现）

| | MCP | A2A |
|---|---|---|
| 管什么 | 一个 Agent ↔ 工具/数据/API | 独立 Agent ↔ 独立 Agent |
| 本项目落点 | `mcp/` 包双侧：Server 侧 `McpToolConfiguration`（把行情/财务工具暴露给 MCP 宿主）；Client 侧 `McpExternalToolClient`（fetchWebPage 经外部 MCP 服务抓网页） | **暂无落点**——本项目是单 Agent 产品 |
| 类比 | JDBC（应用↔数据库） | 企业间 EDI/API 契约 |
| 治理 | AAIF（2025-12 捐入） | AAIF（2026-08 Growth Stage）——两协议同屋 |

关键判词：**两者是同一栈的互补层，不是替代关系**。典型组合用法：库存 Agent 内部用
MCP 查库存，对外用 A2A 把采购单委派给供应商的远端 Agent——MCP 在墙内，A2A 过墙。

## 五、落到本项目的接入点（「会用」档的判定）

本项目当前是**单 Agent + 工具**形态，A2A 的触发场景有两类，都不在当下：

1. **暴露自己**：把「个股研究助手」包装成远端 Agent（Agent Card 声明「股票研究」技能），
   供别的编排器 Agent 委派研究任务——届时我们的三角色管线就是一个 A2A Task 的内部实现，
   HITL 挂起天然映射 input-required 态
2. **调用别人**：研究链路需要外部专才 Agent（如合规审查 Agent、舆情 Agent）时，
   以 A2A 委派而非自建——判定信号=该能力有外部成熟提供方且需要跨组织边界

**不采纳现状的原因**：单进程内多 Agent（我们的 Planner/Builder/Reviewer）用 A2A 是
杀鸡用牛刀——同进程角色协作走 Temporal 编排已足够，A2A 的价值全在跨边界。
重评触发：出现跨组织/跨厂商的 Agent 协作需求时，回到本笔记第五节。

## 六、一句话总结

MCP 让 Agent 够到工具，A2A 让 Agent 够到同事；v1.0 + AAIF 治理让 A2A 从「Google 的
协议」变成「行业的协议」。本项目记住接入点判定即可——同进程协作用编排，跨边界委派用 A2A。
