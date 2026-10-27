# 生产级 Agent 能力地图（活文档）

> 定位：A15 差距台账的永久家。以生产级 Agent 系统全能力域为参照系，
> 标注本项目各项现状与处置（已有 ✅ / 已排期 🔵 / 挂起 ⏸️ / 明确不做 ⛔）。
> 刷新机制：每个里程碑 A14 对账时同步刷新本图；日常 A15 扫描发现新差距随时补行。
> 创建：2026-09-10（首次全维度扫描）。

## 一、模型层

| 维度 | 现状 | 处置 |
|---|---|---|
| 模型选型评测 | ✅ DeepSeek vs Qwen 实测（eval/selection/） | — |
| 模型降级/容错 | ✅ W12 #4 FailoverChatModel：瞬态故障自动切 Qwen，告警+计数器可见，故障注入实测通过 | — |
| 模型路由（档位分流） | 🔵 W25 #2 已排期（规则先行：ROUTE 结果即分档信号，裁决②A） | [w25 计划](plans/advanced/w25-cost-engineering-and-bm25-revisit.md) |
| token 优化 | ✅ prompt 缓存命中率入观测 | — |

## 二、上下文工程

| 维度 | 现状 | 处置 |
|---|---|---|
| 构建（RAG/工具注入） | ✅ 双通道 | — |
| 管理（会话记忆 L1-L5） | ✅ L1 内存版 W10；**L2 持久化 W21 #1③ 落地**（MySQL 8.4 仓储，`AI_CHAT_MEMORY_STORE=jdbc` 切换，inmemory 留缺省）；L3-L5 W24-27 前流量裁决（见 plans/m3/README 五级路线） | — |
| 优化（压缩/摘要/窗口预算） | ❌ | W14+ |
| 语义缓存（相似问题命中答案缓存） | ❌ | W14+（需流量数据） |

## 三、检索管线（纵向解剖：摄入→分块→向量化→召回→精排→生成→引用）

| 阶段 | 现状 | 处置 |
|---|---|---|
| 摄入 | ✅ md + PDF 增量摄入：SHA-256 manifest 三类分派 + 分块参数签名自愈（参数变更自动重建） | [ADR-0012](adr/0012-incremental-ingestion-manifest.md) |
| 分块 | ✅ chunk-size=800 经 400/800/1200 三轮对照数据裁决维持（三轮 Recall@5 均 72%） | [w13-chunk-size-comparison](eval/retrieval/w13-chunk-size-comparison.md) |
| 向量化 | ✅ bge-m3（调研论证选型，重评触发在案）；实测对照（vs Qwen3-Embedding-8B）挂 W15-16 | [embedding-selection-v1](eval/selection/embedding-selection-v1.md) |
| 召回 | ✅ 混合检索（dense+sparse+RRF）W13 数据裁决启用：Recall@5 72%→93%，负例零回归；⚠️ W24 #1a 实锤 sparse 腿长问句近零产出（涉案案两措辞均零命中）——bm25 路线重评排期 W25 #3，数据裁决加固或摘除 | [w13-hybrid-eval](eval/retrieval/w13-hybrid-eval.md) |
| 多主体检索多样性 | ✅ W23 #2 落地：词典制主体识别 + 召回段配额重排（每主体保底 ⌊topK/n⌋ 轮流出列）+ 缺口主体锚定补召；涉案案 buckets=[0,20]→[2,20]，回归 Recall@5 94.3% ≥ 93%，默认启用 | [w23-diversity-quota](eval/retrieval/w23-diversity-quota.md) |
| 查询理解 | ✅ 改写 W11 落地（多轮 eval 6.0/6 vs 关闭 4.5/6 量化收益）；HyDE/查询扩展 ⛔ 现阶段不采纳（W11 #8） | 调研裁决：[hyde-query-expansion-v1](../eval/selection/hyde-query-expansion-v1.md)，重评触发条件在案 |
| 精排 | ✅ rerank 管线 W13 落地（bge-reranker-v2-m3 薄封装+故障降级）；数据裁决 +0pp 保持默认关，重评触发在案 | [w13-rerank-eval](eval/retrieval/w13-rerank-eval.md) |
| 生成 | ✅ prompt 版本化资产 + 零召回防线（W24 #1：空召回重述重试默认开，A/B 探针 3/3 vs 0/3）+ 空检索硬拒答 + 反缝合规则（W9-2）+ 红线告警 | [w24-zero-recall-retry](eval/retrieval/w24-zero-recall-retry.md) |
| 引用忠实度 | ✅ W18 #3 论述结案：三层概念切开（标注率/忠实度/结构化一致性），防线分层 L0/L2 已有、L1 程序化一致性校验候选 W19+、L3 逐句归因明确不做 | [citation-faithfulness](eval/citation-faithfulness.md) |
| 知识图谱/GraphRAG | ❌ | ⛔ 27 周范围外，记录在案 |

## 四、Agent 核心

| 维度 | 现状 | 处置 |
|---|---|---|
| 规划 | ✅ ReAct 显式化 W11 落地（ResearchLoop + 协议解析 + ADR-0015，回归 5.6/6 ≥ 基线）；三角色管线（Planner/Builder/Reviewer）W22 切默认引擎——57 案双臂 +0.33、红线 14/14 满维 | [w22-pipeline-dual-arm](eval/generation/w22-pipeline-dual-arm.md) |
| 工具 | ✅ 三工具 + MCP Server + MCP Client（W12 #1 fetchWebPage 真实调通，ADR-0016） | ✅ W17 收口：行情/财务真源切换 Tushare pro 落地（`4e75ec3`，REST 直连，`ai.stock.data-source` env 切换，mock 留缺省；真源冒烟 IT + tushare 态全链路实测过——真实营收 1741.44 亿/全年日线，2026-10-10）。挂账：Tushare 官方 hosted MCP 体验（AI 宿主自然语言直达 30+ 接口取数，零代码接入）——与 StockDataClient 真源替换（REST 确定性管道）是两条链路，候选 W21-23 工具周（W17 裁决记录） |
| Human-in-the-loop | ✅ W23 #1 接通（裁决①B）：打回耗尽挂起等人工 Signal（approve 放行含背书标注 / reject 驳回 / 超时 fail-closed），审批 API + SSE/日志/Temporal 三处留痕；真机双路径演示过 | [w23-hitl-demo](eval/generation/w23-hitl-demo.md) |
| Skill 渐进披露 | 🔵 机制在库（4 技能：分红口径/年报横读/拒答边界/工具手册），W22 初测 2.0%、W23 扩列复测 9.7% 均 <20% 守线——两轮登记止步，默认 off；重启条件：库 ≥8 或 RAG 通路接注入点 | [w23-skill-token-report-r2](eval/generation/w23-skill-token-report-r2.md) |
| 多 Agent / A2A | 🔵「会用」档认知（W23 #3a 精读笔记：五对象/Task 八态/Agent Card 签名，对照自家 MCP 双侧分工）；不落代码——单 Agent 形态，跨边界委派需求出现时重评 | [a2a-protocol](notes/a2a-protocol.md) |

## 五、评估体系

| 维度 | 现状 | 处置 |
|---|---|---|
| 检索/生成黄金集 + judge 锚定 | ✅ 黄金集（检索 69 / 生成 57，changelog 台账）；judge 校准 W20 复测 63.3% 立案 → W21 输入侧处置（语料时点清单注入）复测 83.3% 过线 → W22 accuracy 维修尺（工具取数如实标注不视为编造）+ 对照案复评确认不松尺 → W24 拒答口径对齐（检索信号注入，涉案案 compliance 0→2、满分案不动） | [w21-calibration-rerun-r2](eval/generation/w21-calibration-rerun-r2.md)、[w24-judge-rescore-2](eval/generation/w24-judge-rescore-2.json) |
| 多轮评测 | ✅ W11（multi-turn 6 条 + 同会话执行器，改写开/关对照入报告） | — |
| Agent 轨迹评估 | ✅ W13 #4 落地：三指标口径 + 批量采集脚本（docs/scripts/trajectory-metrics.py），M3 验收 6/6 收敛 100%、冗余 0 | [m3-acceptance](plans/m3/m3-acceptance.md) 门② |
| 人工反馈回路 | ✅ W24 #3 最小环落地：👍/👎 API + 对话页入口 → badcase 登记（问答快照留证）→ 人工裁定台（scripts/badcase_review.py）→ 黄金集候选区（裁决②A 不自动入集）；真实 badcase 694fcbe6 已走通全流程 | [w24 计划](plans/advanced/w24-recall-robustness-and-feedback-loop.md) |
| 线上影子评估 | ❌ | W14+（需流量） |
| 数据集版本管理 | ✅ golden-set-changelog 台账 | — |

## 六、安全与韧性

| 维度 | 现状 | 处置 |
|---|---|---|
| 红线/红队/注入防御 | ✅ W7 | — |
| 鉴权/限流 | ✅ W12 #2 API Key（默认拒绝+显式放行）+ W12 #3 固定窗口限流（每身份 60/min，429+Retry-After） | — |
| 降级容错 | ✅ 见模型层（W12 #4） | — |
| 内容审核（moderation） | ✅ W18 #3 论述结案：语料低风险成立但三面登记（外部内容入口/输出边界/多轮渗透），最小防线三件套指认，L1/L2 触发条件显式化 | [moderation-minimal-defense](security/moderation-minimal-defense.md) |

## 七、可观测与成本

| 维度 | 现状 | 处置 |
|---|---|---|
| 日志/traceId/token 账本 | ✅ 四级留痕 | — |
| LLM 可观测平台（Langfuse 类） | 🔵 W25 #1 已排期（ADR-0020：OTel GenAI 语义埋点 + Phoenix 先行、Langfuse 对照体验；ADR-0019 第 4 条随之修订） | [ADR-0020](adr/0020-llm-observability-otel-phoenix.md) |
| 成本账 | ✅ 一页制 | — |

## 八、交付与工程

✅ CI / Testcontainers / Jib / K8s / 配置化（A11）/ 文档三层活文档——当前强项，无缺口。

## 扫描方法论（A15 双参照系）

1. **横向切面**：安全 / 交互 / 可复现 / 韧性 / 成本
2. **纵向管线解剖**：RAG 管线与 Agent Loop 逐阶段问"业界生产级这一段长什么样，我们有吗"
3. **本图对照**：逐项过表，状态变化更新

教训来源：2026-09-10 首轮扫描只做横向切面，reranking（管线精排段）漏网——
清单的盲区导致按清单扫描必有盲区，参照系必须成对使用。
