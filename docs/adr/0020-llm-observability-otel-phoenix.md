# ADR-0020：LLM 可观测平台——OTel GenAI 语义埋点 + Phoenix 先行（Langfuse 对照）

- 状态：已接受
- 日期：2026-10-27（W25 #1 立项，Owner 三裁决：①A ②A ③排期进 W25 步骤一）

## 背景

本项目可观测性一直停在「日志级」：traceId 四级留痕（路由/检索/工具/模型）、SSE 轨迹、
tool-audit 日志、token 账本、actuator 指标——排障靠 grep，没有可视化全链路追踪界面。
ADR-0019 第 4 条当时裁决「不上 OTel/Prometheus 栈（重型 infra），多副本部署时重评」。

Owner 明确提出：需要 web console 把可观测性与全链路追踪提升到生产级别，结合业界标准。
**ADR-0019 的重评条件以另一种形式触发**——不是多副本，而是「生产形态」目标本身：
24h 无人值守（P5）没有可视化追踪就是裸奔，分层故障定位 SOP（L4 运营验收③）需要
链路界面作载体。

## 业界格局核实（2026-10-27 检索）

- OpenTelemetry GenAI 语义约定（gen_ai.* 字段族）已成各平台通用摄入标准——埋点走它，
  后端可换不锁定；
- Langfuse（MIT 核心，v3 OTel 原生，已被 ClickHouse 收编）：功能最全（链路/prompt 管理/
  数据集/评测/成本看板），自托管运维重（web+worker+Postgres+ClickHouse+Redis+对象存储）；
- Arize Phoenix（Elastic License 2.0）：单容器轻量自托管，OTel/OpenInference 原生，
  链路/RAG 段/评测视图强；
- Helicone 代理网关只有请求级视图，看不到 ReAct 逐步轨迹——与本项目「轨迹可见」
  核心诉求错位，出局；LangSmith 等 SaaS 数据出本机，出局；纯自建无标准背书，出局。

## 决策

1. **埋点层走 OpenTelemetry GenAI 语义约定**（裁决①A）：Micrometer→OTLP 桥接，
   Spring AI 自带 observation 点之上补 RAG/检索/工具/ReAct 步的 gen_ai.* 属性。
   这是唯一不可逆决策点——走标准则后端永远可换。
2. **首个 console 接 Phoenix**（裁决②A）：单容器半天跑通，OTLP 直连；
   **Langfuse 作对照体验**（部署起来对比功能面，重运维是否值回票价由实机裁决），
   数据裁决是否常驻——符合本课程「候选积累→数据裁决」惯例。
3. **排期 W25 #1**（裁决③）：与本周成本仪表盘/预算熔断天然同域（token 计量数据
   经 OTel 指标通道进同一界面），24h 无人值守（W26）的安全前置由此齐备。
4. **ADR-0019 第 4 条就此修订**：「不上 OTel 栈」作废，替换为本决策；其「traceId
   三面同键」原则保留并扩展——traceId 同时作为 OTel trace 属性，日志面与链路面同键互查。

## 备选方案

- **Grafana 经典栈（Collector+Jaeger/Tempo+Prometheus+Grafana）**：最正统生产标准，
  但 LLM 专属视图（prompt 内容、token 成本归因）需自建面板，课程时间投入产出低；
  保留为后端替换选项（OTel 埋点不换）。
- **Langfuse 直接上完整平台**：功能最全但 6 组件运维重，且最富功能假设自家 SDK——
  与裁决①的「不锁定」原则张力大。作对照体验项，不否决。
- **Helicone/纯自建**：见背景出局理由。

## 后果

- 正面：全链路追踪可视化（ReAct 步/检索段/工具调用/模型调用一图串联）；token 成本
  按 trace 归因（W25 #1 仪表盘的天然数据源）；分层故障定位 SOP 有界面载体；
  埋点技能从「会用」推进到「会造」（蓝图可观测性条目）。
- 代价：Phoenix 容器常驻 + OTLP exporter 依赖；Langfuse 对照体验约半天。
- 重评触发：Phoenix 功能面不足 / Langfuse 对照碾压 / 多端部署需求出现。
