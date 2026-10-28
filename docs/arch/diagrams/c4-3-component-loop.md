# C4 L3：组件图 · loop 研究循环域

> 本张聚焦 **loop 包**——三角色管线（sub agent）怎么编排、Temporal 在哪一层、
> HITL 断点怎么挂。截止 2026-10-28（W24 收官态，engine 缺省=pipeline）。

```mermaid
C4Component
  title 组件图：loop 研究循环域（com.aievolution.loop）

  Component_Ext(agentchat, "AgentChatService", "chat 域", "研究循环调用方")
  Component_Ext(approvalctl, "ApprovalController", "chat 域", "HITL Signal 来源")

  Container_Boundary(loop, "loop 域（主进程内）") {
    Component(engine, "ResearchLoopEngine", "引擎路由（ai.loop.engine 旗标）", "pipeline（缺省）/ temporal / inline 三态；inline 留作对照臂")
    Component(pipeengine, "TemporalPipelineEngine", "适配器", "把引擎调用翻译成 Temporal workflow 启动/Signal/Query")
    Component(wf, "ResearchPipelineWorkflowImpl", "Temporal workflow（确定性）", "编排：plan→build→review 串行；打回批注回流 ≤2 次；耗尽→拒答或 HITL 挂起")
    Component(acts, "ResearchPipelineActivitiesImpl", "Temporal activities", "plan/build/review/hitlEvent 四个活动；非确定性副作用全在这层")

    Component(planner, "Planner / DeepSeekPlanner", "角色端口", "纯规划：问题+历史 → Plan（子问题清单）；无工具不碰数据；不可解析降级单子问题")
    Component(builder, "Builder（build activity 内）", "ReAct 循环执行体", "逐子问题跑 ReAct 取证（ChannelToolExecutor 按 Channel 派工具）+ 起草合成 DraftAnswer")
    Component(reviewer, "Reviewer / DeepSeekReviewer", "角色端口", "独立复核：只审不做；溯源/合规检查，打回必带批注")

    Component(loop, "ResearchLoop", "单循环实现", "显式 ReAct（ADR-0015）：Thought/Action/Observation 逐轮，max-steps=6")
    Component(parser, "ReactProtocolParser", "协议解析+容错", "协议违背兜底（W20 容错修复：混排/截断/中文标记）")
    Component(toolreg, "ToolRegistry / ToolExecutor", "工具手册+执行", "工具白名单派发，渲染 {TOOL_MANUAL} 注入 prompt")
    Component(model, "ResearchModel / DeepSeekResearchModel", "模型端口", "循环内单轮对话抽象；供应商瞬态故障由 FailoverChatModel 兜底")
  }

  System_Ext(temporal, "Temporal Server", "workflow 状态持久化/任务派发/计时器")
  System_Ext(deepseek, "DeepSeek API")

  Rel(agentchat, engine, "runResearch(question, history, traceId)")
  Rel(engine, pipeengine, "engine=pipeline")
  Rel(engine, loop, "engine=inline（对照臂）")
  Rel(pipeengine, wf, "启动 workflow", "经 Temporal Client")
  Rel(wf, temporal, "事件历史/定时器", "gRPC")
  Rel(acts, temporal, "轮询任务/回报结果", "worker 线程池")
  Rel(approvalctl, wf, "Signal: approve/reject", "经 Temporal Client")

  Rel(wf, acts, "plan(input) → Plan")
  Rel(wf, acts, "build(plan, comment) → BuildOutcome")
  Rel(wf, acts, "review(outcome) → ReviewVerdict")
  Rel(wf, acts, "hitlEvent / await 挂起")

  Rel(acts, planner, "plan 活动")
  Rel(acts, builder, "build 活动")
  Rel(acts, reviewer, "review 活动")
  Rel(builder, toolreg, "工具取证（白名单）")
  Rel(builder, parser, "协议解析")
  Rel(planner, model, "LLM 调用")
  Rel(builder, model, "LLM 调用")
  Rel(reviewer, model, "LLM 调用")
  Rel(loop, parser, "协议解析")
  Rel(loop, toolreg, "工具执行")
  Rel(model, deepseek, "HTTPS")
```

## 读图要点

- **「sub agent」的真身**：Planner/Builder/Reviewer 是三个 Java 接口 + 三套专属 prompt
  + 收窄的输入输出契约，背后是同一个 DeepSeek 模型；角色隔离的价值在**上下文隔离**
  （Reviewer 只拿到草稿成品，不见 Builder 推理过程），不在进程隔离。
- **workflow/activity 分层是 Temporal 纪律**：workflow（编排）必须确定性——只用入参、
  activity 返回值、Workflow API；一切副作用（LLM 调用、日志留痕）都在 activity 层。
  进程崩溃后 Temporal 按事件历史重放恢复断点。
- **打回回路**：review 打回 ≤2 次，批注回流 Builder 重写；耗尽后两条路——拒答文案
  （宁可不答不可错答）或 HITL 挂起等人工（默认开，超时 fail-closed）。
- **重试策略分层**：plan/review 快活动（120s 超时、attempts=2）；build 含多轮模型+工具
  调用放宽到 600s。
- **三引擎旗标**：pipeline 缺省（W22 双臂验收 +0.33 且红线满维后切默认）；temporal=
  单循环事件历史版；inline=进程内直跑，留作 A/B 对照臂。
