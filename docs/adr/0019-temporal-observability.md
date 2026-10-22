# ADR-0019：Temporal 引入后的可观测分层——日志/事件历史/指标三面分工

- 状态：已接受
- 日期：2026-10-22（W21 #1② 落地，#1④ 成文）

## 背景

W21 #1② 引入 Temporal 作为 ResearchLoop 的第二引擎（`ai.loop.engine=inline|temporal`，路线 C 旗标双引擎）。可观测性随之出现新问题：

1. **线程上下文断裂**：Temporal activity 在 worker 线程池执行，MDC 不随调用链自动传递——inline 引擎赖以串联的 traceId 日志链（research-trace）在 temporal 引擎下会断成孤段。
2. **观测面翻倍**：同一研究循环，inline 只有应用日志一面；temporal 多了事件历史（Event History）与 Web UI 一面。两面若各说各话，排障时要在两套语义间人肉翻译。
3. **指标缺口**：Temporal SDK 自身的 worker/activity 指标（poll 延迟、执行耗时）尚未接入任何后端。

需要一份裁决：trace 怎么续、三面怎么分工、指标接不接。

## 决策

1. **traceId 手工透传，三面同键**：traceId 经 `ResearchRunInput` 入参跨 Temporal 边界交接，activity 内重建 MDC；workflowId 直接取 `research-<traceId>`——应用日志、Temporal 事件历史、Web UI 三个观测面用**同一个键**互查，不做任何映射表。
2. **观测分层分工**：
   - **业务语义看应用日志**（thought/action/observation 轨迹、收敛原因）——temporal 引擎保持 research-trace 同一 logger 与字段格式，仅多一个 `engine=temporal` 标记，grep 习惯不变；
   - **执行语义看事件历史 + UI**（重试次数、断点恢复点、每 activity 耗时）——这类问题不去日志里找，直接 `localhost:8233` 按 workflowId 查；
   - **Query 方法**（`currentSteps`/`status`）作为运行中实例的只读探针，是排障第三通道。
3. **inline 引擎观测形态零改动**：旗标切回 inline 后日志/轨迹与 W20 完全一致，双引擎不在观测面引入分叉。
4. **指标暂不接后端**：不上 OpenTelemetry/Prometheus 栈（本地课程项目，collector+backend 属重型 infra）；Temporal 自带 UI 的计数视图 + 应用 actuator 已覆盖当前排障需求。SDK micrometer 指标出口留作待办锚点。

## 备选方案

- **OpenTelemetry 全链路（Temporal OTel interceptor + OTel Java agent）**：跨 workflow/activity/应用的真分布式追踪，语义最完整；但需要 collector 与后端（Jaeger/Tempo）常驻，课程阶段投入产出不成比例。否决，多副本部署时重评。
- **事件历史当唯一观测面，业务日志弱化**：事件历史记录的是 payload 不是语义，thought 轨迹在 UI 里读体验差，且 dev server in-memory 重启即丢。否决。
- **自建 run 状态表 + 后台指标采集**：与 Temporal 事件历史功能重叠，重复造轮子。否决。

## 后果

- 正：真机冒烟实证三面同键生效——日志 `traceId=b2ab50e4` ↔ UI workflow `research-b2ab50e4` Completed ↔ 事件历史 23 条（W21 #1② 实录）；57 案 eval 对照批在 temporal 引擎下 trace 链全程不断。
- 正：排障路径有了明文分工，"重试几次、卡在哪步"不再翻日志猜。
- 负/权衡：traceId 透传是手工约定而非框架保证——未来新增 activity 入参时若漏接 traceId，链条静默断裂（单测 `ResearchLoopWorkflowTest` 锁定现有交接，新增 activity 需自觉沿用）。
- 负/已知边界：dev server in-memory，事件历史随 server 重启丢失——持久化历史需正式 Temporal 集群，属部署议题不在本 ADR。
- 待办：Temporal SDK micrometer 指标出口（poll/activity 耗时）接 actuator；OTel 全链路与多副本部署一并重评。
