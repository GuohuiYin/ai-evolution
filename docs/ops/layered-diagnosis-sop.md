# 分层诊断 SOP：故障定位在 L 几

> 2026-10-30 成文（W26 #1）。蓝图 4.8「会造」档验收物：L4 运营验收门③要求
> 「注入故障 30 分钟内用分层诊断 SOP 定位到正确的层」——本表是计时依据。
> 前身：蓝图失败归因对照表（§1.3，实战期全程在用，MCP stdio 失活定位即实例）；
> 本 SOP 把它从「三条经验」升级为「全层对照 + 仪器路由 + 计时口径」。

## 1. 观测面地图（六件仪器，同键 traceId）

| 仪器 | 看什么 | 入口 |
|---|---|---|
| Phoenix trace 面板 | 全链路 span：ReAct 步/检索段/工具调用/模型调用 + token 归因 | `localhost:6006`（OTel GenAI 埋点，[ADR-0020](../adr/0020-llm-observability-otel-phoenix.md)） |
| traceId 四级日志 | 业务语义：路由/检索/工具/模型四级留痕；`research-trace` logger 带 `engine=inline/temporal/pipeline` 标记 | 应用 stdout，`grep <traceId>` |
| Temporal 事件历史 + UI | 执行语义：重试次数、断点恢复点、每 activity 耗时；`workflowId=research-<traceId>` | `localhost:8233`（[ADR-0019](../adr/0019-temporal-observability.md) 三面分工） |
| token 台账 | 逐笔 token/成本流水（重启不丢）+ 日聚总 | `data/cost-ledger.jsonl` + `GET /ai/cost/summary` |
| actuator | 健康探针（liveness/readiness 分离）+ Micrometer 指标 | `/actuator/health` 等 |
| tool-audit | 工具调用审计：入出参摘要、耗时、异常 | 应用日志 tool-audit 记录 |

排障第三通道：Temporal Query 探针（`currentSteps`/`status`）——运行中实例的只读窥视孔。

## 2. 分层对照表（故障现象 → 特征信号 → 第一仪器 → 处置）

| 层 | 典型故障现象 | 特征信号（怎么认出是这层） | 第一仪器 | 处置方向 |
|---|---|---|---|---|
| **L0 模型边界** | 知识截止外的事实答错；给了正确上下文仍答错/硬编 | 检索段命中且内容正确，模型输出仍错；换措辞重问同样错 | Phoenix span 看模型实收的上下文与输出 | **不是 bug**——补语料（转 L2）或加约束（转 L3），别在 prompt 措辞上打转（蓝图失败归因对照表先例） |
| **L1 Prompt / 格式漂移** | 协议违背（Thought 泄漏、中文标记 preamble）、输出格式漂移、拒答口径漂移 | `protocol violation` WARN；终答正则扫出「思考/行动：」；生成 eval 跌但检索 eval 稳 | 四级日志（research-trace）+ Phoenix 消息内容 span | prompt 版本修复走 A13 eval 门（附跑批摘要才可提交）；parser 容错兜底；必要时回滚 prompt 版本 |
| **L2 Context / 召回缺失** | 该答的硬拒答、答错库内已有的事实、来源时点错位 | dense/sparse 腿分数贴阈或全灭（检索段 0 hit）；检索黄金集 Recall 跌 | 四级日志之检索级 + 检索黄金集 eval + Qdrant 面板 | 顺序排查：摄取管线（源变了？）→ 阈值悬崖 → embedding 漂移；措辞敏感由零召回重试防线兜（W24） |
| **L3 Harness / 护栏与工具** | 工具超时/失活、买卖建议泄漏、越权调用、循环不收敛 MAX_STEPS | tool-audit 异常记录；RedLineGuard 触发；`exit stopReason=MAX_STEPS` | tool-audit + 四级日志之工具级 | 外部资源走横切行三分对照；护栏缺陷入 compliance 域单点修（A12）；超时/重试参数显式化（ADR-0013 口径） |
| **L4 Loop / 编排** | 空转不收敛、预算失控、HITL 卡死、打回无限循环 | 事件历史重试风暴；台账斜率陡增；预算熔断 503 | Temporal 事件历史（UI 按 workflowId 查）+ `/ai/cost/summary` | Query 探针看 `currentSteps` 卡在哪步；确认熔断留痕；引擎旗标切 `inline` 对照隔离编排层变量 |
| **横切：外部资源**（Qdrant / MCP / 模型 API） | 间歇性失败、超时、连接异常 | **三分对照**：实例内复现失败 vs 独立最小探测正常 vs 清洁重启恢复——三件套齐 = 长驻状态腐败 | 独立探针 + actuator 健康 + tool-audit | 重连/降级/failover（W14 先例：失活重建+有界重试一次）；不要先在应用层找不存在的 bug |

## 3. 通用定位三步法

1. **定症状层**：从用户可见症状出发，用对照表「特征信号」列圈定 1-2 个候选层——
   症状永远先看最上层可观察面（用户看到什么），再往下钻。
2. **同键互查**：拿 traceId 串三面——Phoenix 链路先视觉定位异常 span（红/慢/缺段）→
   四级日志取语义细节（thought/action/observation、检索分数、收敛原因）→
   Temporal 事件历史看执行语义（重试/耗时，仅 temporal/pipeline 引擎）；
   成本类疑问加台账（`/ai/cost/summary` 按通路/模型分面）。
3. **对照处置**：按层执行处置列；修完回套——该层特征信号应消失，涉案 eval 案转绿，
   两项都见到才算定位正确（否则归错层，回第一步）。

## 4. 计时口径（验收门③）

从「故障被发现/注入」起，到「说出正确的层 + 出示第一仪器中的特征信号
（日志行/span 截图/事件历史记录）」止——**定位不含修复**。目标 ≤30 分钟。

## 5. 回套验证（历史真实故障）

见下节「回套验证记录」——每起真实故障定位后回套本表：对得上打勾留痕，
对不上**先修表再收工**（同黄金集轮换纪律：表是活资产，不是一次性文档）。
