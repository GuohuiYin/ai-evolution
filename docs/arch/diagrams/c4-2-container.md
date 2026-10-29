# C4 L2：容器图（Container）

> C4 第二级：系统内部的「可部署/可运行单元」。视角问题：**系统由哪些进程/存储组成，
> 各自的技术选型和职责是什么？** 截止 2026-10-29（W25 #1b Phoenix 落地）。

```mermaid
C4Container
  title ai-evolution 容器图

  Person(user, "终端用户 / Owner")

  System_Boundary(aievo, "ai-evolution") {
    Container(web, "聊天页", "静态 HTML/JS（static/index.html）", "提问、SSE 流式渲染过程轨迹、👍/👎 反馈、HITL 审批操作")
    Container(app, "应用主进程", "Java 21 / Spring Boot / Spring AI，端口 18080", "单一部署单元：HTTP API + 对话编排 + 研究循环 + RAG + Temporal worker 全在进程内")
    ContainerDb(feedback, "反馈登记库", "JSON 文件（data/feedback.jsonl，不入 git）", "👎 badcase 待审池，人工裁定后回流黄金集候选区")
    Container(evalpy, "eval 离线工具链", "Python 脚本（scripts/）", "judge 校准工作表生成/分析、双臂对照报告、badcase 复审 CLI")
  }

  System_Ext(deepseek, "DeepSeek API")
  System_Ext(siliconflow, "SiliconFlow（embedding/rerank/降级模型）")
  System_Ext(tushare, "Tushare API")
  System_Ext(mcpclient, "外部 MCP 客户端")

  System_Boundary(local_infra, "本地依赖（docker / 进程）") {
    ContainerDb(qdrant, "Qdrant", "docker 容器，gRPC 6334", "向量库：dense + sparse 双路（混合检索，RRF 融合）")
    ContainerDb(mysql, "MySQL", "docker 容器 ai-evolution-mysql，3306", "会话记忆 L2：重启不失忆、多副本同源（旗标 jdbc 启用）")
    Container(temporal, "Temporal Server", "temporal server start-dev，gRPC 7233", "workflow 持久化与任务派发：崩溃重放恢复断点")
    Container(mcpfetch, "mcp-fetch", "uvx stdio 子进程（本地）/ K8s Deployment（部署态）", "官方 mcp-server-fetch：外部网页抓取")
    Container(phoenix, "Phoenix", "docker 单容器 ai-evolution-phoenix，UI 6006（W25 落地）", "OTLP 接收（HTTP 6006/v1/traces）+ trace 树 web console")
  }

  Rel(user, web, "使用", "HTTPS")
  Rel(web, app, "REST + SSE", "POST /ai/chat /chat/stream /ai/feedback /api/approvals")
  Rel(mcpclient, app, "工具调用", "MCP Streamable HTTP POST /mcp")

  Rel(app, qdrant, "向量检索/写入", "gRPC")
  Rel(app, mysql, "会话历史读写（旗标 jdbc 时）", "JDBC")
  Rel(app, temporal, "workflow 启停/Signal/Query", "gRPC")
  Rel(app, mcpfetch, "网页抓取", "MCP stdio / Streamable HTTP")
  Rel(app, deepseek, "聊天/规划/复核/judge", "HTTPS")
  Rel(app, siliconflow, "embedding / rerank / failover", "HTTPS")
  Rel(app, tushare, "行情财务（旗标 tushare 时）", "HTTPS REST")
  Rel(app, feedback, "badcase 登记/读取", "文件 IO")
  Rel(app, phoenix, "trace（metrics #2 再开）", "OTLP HTTP 6006/v1/traces")

  Rel(evalpy, app, "驱动 eval 跑批（HTTP/内部 API）")
  Rel(evalpy, feedback, "badcase 复审裁定", "文件 IO")
```

## 读图要点

- **单进程内核**：应用主进程是唯一业务计算单元——三角色管线的「sub agent」是进程内
  角色隔离（接口+prompt+Temporal 编排），不是多进程（参见 loop 组件图）。
- **部署形态双 profile**（application.yml 尾部）：本地 default=stdio 拉起 mcp-fetch
  子进程；K8s profile=Streamable HTTP 连独立 mcp-fetch Service（契约不变式，上层零感知）。
- **K8s 清单**（`k8s/`）：应用 Deployment/Service/ConfigMap + qdrant + mcp-fetch
  （含 NetworkPolicy）；MySQL/Temporal 当前仅本地容器，未入 K8s 清单。
- **旗标化外部依赖**：记忆仓储（inmemory/jdbc）、股票数据源（mock/tushare）、
  循环引擎（pipeline/temporal/inline）、sparse 实现（matchtext/bm25）、rerank/hybrid/
  diversity/zero-recall-retry——全部配置裁决，多数经黄金集数据裁决定档（见课程地图触发点清单）。
