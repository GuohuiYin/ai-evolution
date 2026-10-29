# C4 L1：系统上下文图（System Context）

> C4 第一级：系统全貌——ai-evolution 与「人」和「外部系统」的关系。
> 视角问题：**这个系统给谁用、依赖谁、被谁依赖？**
> 截止 2026-10-29（W25 #1a/#1b 落地：Phoenix 接通）。

```mermaid
C4Context
  title ai-evolution 系统上下文（W25 #1b 落地态）

  Person(user, "终端用户", "Web 聊天页提问，SSE 流式看回答与过程轨迹；可对回答点 👍/👎")
  Person(owner, "Owner / 审批人", "HITL 挂起时放行/驳回；eval 打分校准；badcase 人工裁定")
  Person(operator, "评估运营（同 Owner 兼职）", "离线跑批：检索/生成黄金集评估、judge 校准、badcase 复审")

  System(aievo, "ai-evolution", "股票研究问答 Agent：RAG + 工具调用 + 三角色管线（plan→build→review）+ HITL")

  System_Ext(deepseek, "DeepSeek API", "主聊天模型（对话/改写/规划/复核/judge）")
  System_Ext(siliconflow, "SiliconFlow", "Embedding（bge-m3）+ Reranker（默认关）+ 故障降级聊天模型（Qwen）")
  System_Ext(tushare, "Tushare API", "股票行情/财务真源（旗标切换，缺省 mock）")
  System_Ext(qdrant, "Qdrant", "向量库：dense + sparse（混合检索）")
  System_Ext(mysql, "MySQL", "会话记忆 L2 持久化（旗标切换，缺省内存）")
  System_Ext(temporal, "Temporal Server", "workflow 编排与事件历史：三角色管线/单循环的执行底座")
  System_Ext(mcpfetch, "MCP fetch 服务", "外部网页抓取（本地 uvx stdio 子进程 / K8s 独立工作负载）")
  System_Ext(mcpclient, "外部 MCP 客户端", "经 MCP Server（Streamable HTTP）调用本系统能力")
  System_Ext(phoenix, "Phoenix", "LLM 可观测平台：OTLP 接收 trace，web console（W25 #1b 落地）")

  Rel(user, aievo, "提问/流式接收/反馈", "HTTPS + SSE")
  Rel(owner, aievo, "审批 Signal（approve/reject）", "HTTPS /api/approvals")
  Rel(operator, aievo, "eval 跑批（离线入口）")
  Rel(mcpclient, aievo, "工具调用", "MCP Streamable HTTP /mcp")

  Rel(aievo, deepseek, "聊天/改写/规划/复核/评分", "HTTPS")
  Rel(aievo, siliconflow, "向量化/精排/降级聊天", "HTTPS（OpenAI 兼容）")
  Rel(aievo, tushare, "行情财务取数", "HTTPS REST")
  Rel(aievo, qdrant, "向量读写/全文索引", "gRPC 6334")
  Rel(aievo, mysql, "会话历史读写", "JDBC 3306")
  Rel(aievo, temporal, "workflow 任务派发/事件历史", "gRPC 7233")
  Rel(aievo, mcpfetch, "网页抓取", "MCP stdio / HTTP")
  Rel(aievo, phoenix, "trace 上报（metrics 通道 #2 再开）", "OTLP HTTP 6006")
```

## 读图要点

- **单一部署单元**：ai-evolution 是一个 Spring Boot 进程；Qdrant/MySQL/Temporal/mcp-fetch
  均为外部依赖（本地容器或 K8s 独立工作负载）。
- **供应商分工**（ADR-0005）：聊天=DeepSeek，向量/精排=SiliconFlow；DeepSeek 故障时
  降级到 SiliconFlow 托管的 Qwen（W12 failover）。
- **人在环上三种姿态**：终端用户（提问+反馈）、审批人（HITL 断点裁决）、评估运营
  （离线跑批与校准）——三者当前都是 Owner 一人，架构上已分立。
- **W25 落地**：Phoenix 接通后，「trace 上报」成为第四条出向数据通路（ADR-0020）——
  链路面板真实可查（证据：`docs/notes/images/w25-phoenix-*.png`）；metrics 通道保持关闭——
  #2 成本台账走本地 JSONL 文件（账本须独立于观测栈存活，fail-closed 前提），未复用 OTel 指标通道
