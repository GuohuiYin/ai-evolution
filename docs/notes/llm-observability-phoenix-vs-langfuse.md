# Phoenix vs Langfuse 对照笔记（W25 #1c）

> 日期：2026-10-29　环境：Phoenix（ai-evolution-phoenix，单容器）vs Langfuse v4.71.0 OSS 自托管全家桶（/tmp/langfuse-w25，对照后拆除）
> 数据：同一应用同一埋点（OTLP 直发），RAG 问答「贵州茅台最近一次分红是多少钱」+ Agent 问答「600519 最新收盘价是多少」各发一次

## 结论先行

**链路渲染两家打平，Langfuse 功能面大但与我们自建路线重叠严重，运维重 6 倍。裁决建议：Langfuse 不常驻，Phoenix 维持 ADR-0020 的日常面板地位。**

Langfuse 的差异化能力（prompt 版本管理、数据集/评测工作流、人工标注、成本看板）恰好都是本项目已有或计划自建的部分：prompt 版本化在 git，eval 体系自建（W19 起），成本看板是 W25 #2 自建目标。为一个本地学习项目常驻 6 个容器、2.9 GiB 内存换这些重叠能力，不值。

## 实测记录

### 接入方式

- Langfuse v4 是 events_only 模式：老的 `/api/public/traces` REST 摄入已关闭，**只收 OTLP**（`POST /api/public/otel/v1/traces`），我们的导出链路零改动即可对接。
- 鉴权：Basic auth（public key : secret key）。踩坑记录：**Spring Boot CLI 参数 `--...otlp.headers.Authorization=...` 的 map 型属性绑定不上**（导出 401，curl 同 header 直测通过）；换成环境变量 `MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_HEADERS_AUTHORIZATION` 即通。此坑与 Langfuse 无关，是 Boot 宽松绑定的 CLI 形态问题。
- 密钥初始化后自动创建项目 `ai-evolution`（id `aievo`）。

### 链路渲染对照（同一批 span，两家各看一遍）

| 维度 | Phoenix | Langfuse v4 |
| --- | --- | --- |
| 树形嵌套 | ✅ | ✅ 同等清晰，另有 Timeline/Graph/Data 三种视图切换 |
| 生成段识别 | `openinference.span.kind=LLM` 图标 | ✅ `chat deepseek-v4-flash` 紫色生成图标 + 树上直接带 token 徽标（1,231 tokens） |
| 工具段识别 | TOOL kind 图标 | ✅ `getDailyQuotes` 扳手图标；`input.value`/`output.value` 映射进 Input/Output 面板（入参 `600519,2025-01-01,2025-12-31`、出参「数据源未覆盖…」完整可见） |
| 检索段识别 | RETRIEVER kind 图标 | ✅ `rag.retrieval` 放大镜图标，Input 为用户问题；子段 `embedding BAAI/bge-m3` 也被识别为生成段（4 tokens 徽标） |
| 业务号互查 | `app.trace_id` 属性可查 | ✅ 同样在 span 属性里（`attributes.app.trace_id`），无专门筛选入口，需走 metadata 过滤 |
| prompt/response 内容 | ❌ 无（Spring AI 默认不上报消息体） | ❌ 同样无（Input/Output 面板显示 undefined）。**两家打平的盲区**，要看得开内容捕获 |
| 成本列 | ❌ 无此概念 | ⚠️ 有 Cost 列但全程「—」：`deepseek-flash` 不在内置价目，需自建模型价目才出数（未深验，标为推测） |
| 列表形态 | 按 trace 分组 | v4 是 observation 平铺表，根 span 才有 Trace Name；习惯后无异，初看略乱 |

### 功能面对照

Langfuse v4.71.0 OSS 侧栏全量：Home、Dashboards、Observability（Tracing / Sessions / Users / Alerts）、Prompt Management（Prompts / Playground）、Evaluation（Scores / Evaluators / Human Annotation）、Datasets（Experiments）。Phoenix 侧：Tracing + 简单的 prompt/dataset/eval 实验功能。

Langfuse 独有且像样的：Sessions/Users 维度聚合（需要埋 `langfuse.session.id` 类属性才有数，我们没埋，实测为空）、告警、人工标注队列、Playground。**但对本项目全部落在重叠区或非必需区**。

### 运维对照（docker stats 实测）

| | Phoenix | Langfuse |
| --- | --- | --- |
| 容器数 | 1 | 6（web / worker / postgres / redis / clickhouse / minio） |
| 常驻内存 | 483 MiB | ≈ 2.9 GiB（clickhouse 1.1 GiB + web 829 MiB + worker 693 MiB + 其余） |
| 起栈 | 一条 docker run | compose 全家桶 + 初始化密钥/建项目 |

## 裁决建议

1. **不常驻 Langfuse**，维持 ADR-0020：Phoenix 是日常 LLM 面板。对照栈用完即拆（`docker compose down -v`）。
2. 本笔记归档为裁决依据；未来若引入人工标注或外部评测协作，再评估 Langfuse（或其时的新选项），届时重开 ADR。
3. 附带发现留给后续任务：
   - prompt/response 内容两家面板都看不到——是否开启 Spring AI 消息内容捕获，列入 W25 #1 收尾时一并裁决（涉及隐私面与存储量）。
   - Langfuse 成本列为空提示我们：模型名是 `deepseek-flash` 这类自起名时，任何面板的内置价目都匹配不上，W25 #2 自建成本台账反而更可控。

## 证据

- 功能面导航：[w25-langfuse-nav.png](images/w25-langfuse-nav.png)
- Agent 链路树（工具段扳手图标 + 生成段 token 徽标）：[w25-langfuse-agent-trace.png](images/w25-langfuse-agent-trace.png)
- RAG 链路树（rag.retrieval 放大镜 + embedding 子段）：[w25-langfuse-rag-trace.png](images/w25-langfuse-rag-trace.png)
- Phoenix 侧对照图见 #1b：w25-phoenix-rag-trace.png、w25-phoenix-agent-tool-trace.png
