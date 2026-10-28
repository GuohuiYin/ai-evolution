# 纵切视图：四条端到端业务链路

> 补充视图（非 C4 静态层级）：挑四条最有代表性的业务链路，从用户动作一路画到外部依赖
> 再画回来——每条链路纵穿所有域。读法：**一条链路就是一次完整的「竖切片」**。
> 截止 2026-10-28（W24 收官态）。

## 纵切①：RAG 知识问答链路（/ai/chat 主流通路）

```mermaid
sequenceDiagram
  autonumber
  actor U as 用户（聊天页）
  participant C as ChatController
  participant R as ChatRouter
  participant S as RagChatService
  participant M as ChatMemory
  participant Q as QueryRewriter
  participant K as 检索链（dense+sparse→RRF→diversity）
  participant P as PromptLibrary
  participant F as FailoverChatModel→DeepSeek
  participant QD as Qdrant

  U->>C: POST /ai/chat/stream（问题+会话 ID）
  C->>R: 分流判定
  R->>S: 知识问答类 → RAG 管道
  S->>M: 读会话历史（窗口 20 条）
  S->>Q: 指代消解+规范化（温度 0）
  Q->>K: 改写后查询
  K->>QD: dense 向量 + sparse 字面双路召回
  QD-->>K: 召回集（topK=20）
  K-->>S: RetrievalEvidence（截断 top-5，含主体配额重排）
  alt 召回为空（阈值悬崖防线，W24）
    S->>Q: 换措辞重述再试一次
    Q->>K: 重述查询 → 重检索
  end
  alt 仍为空
    S-->>U: 硬拒答（宁可不答不可错答）
  else 有证据
    S->>P: 渲染 rag-chat-v3（证据+红线约束）
    S->>F: 生成（流式）
    F-->>U: SSE 逐 delta 回推（含引用）
    S->>M: 写回本轮问答
  end
```

**关键坐标**：阈值 0.5 悬崖在检索链内（W24 病灶）；零召回重试挂在 RagChatService；
引用校验+红线在生成后出口侧。

## 纵切②：研究循环链路（Agent 通路，含 HITL）

```mermaid
sequenceDiagram
  autonumber
  actor U as 用户
  actor O as Owner（审批人）
  participant A as AgentChatService
  participant E as ResearchLoopEngine（pipeline 缺省）
  participant T as Temporal Server
  participant W as PipelineWorkflow
  participant AC as Activities（plan/build/review）
  participant DS as DeepSeek
  participant TL as 工具（stock/MCP fetch）

  U->>A: 复杂研究类问题（SSE 流式）
  A->>E: runResearch(question, history, traceId)
  E->>T: 启动 workflow
  T->>W: 编排开始
  W->>AC: plan → Planner 拆子问题
  AC->>DS: 规划调用
  loop build→review 回路（打回 ≤2 次）
    W->>AC: build（含打回批注）
    AC->>DS: 逐子问题 ReAct 取证
    AC->>TL: 工具调用（审计切面记录）
    AC-->>W: BuildOutcome（草稿+轨迹）
    W->>AC: review → Reviewer 独立复核
    AC->>DS: 复核调用
    AC-->>W: ReviewVerdict（过/打回+批注）
  end
  alt 复核通过
    W-->>U: SSE 终答（FINAL_ANSWER）
  else 打回耗尽 且 HITL 开（默认）
    W->>T: 挂起（Workflow.await，上限 2h）
    T-->>U: SSE 挂起事件（断点可见）
    O->>T: /api/approvals Signal（approve/reject）
    alt approve
      W-->>U: 放出草稿+人工背书标注
    else reject 或超时
      W-->>U: fail-closed 拒答
    end
  end
```

**关键坐标**：workflow 只编排不碰副作用；崩溃后 Temporal 按事件历史重放续跑；
HITL 缺席策略显式化——超时未决往「拒答」倒（W23/W24）。

## 纵切③：人工反馈闭环链路（👎 → 黄金集候选）

```mermaid
flowchart LR
  U["用户点 👎"] --> FC["FeedbackController<br/>POST /ai/feedback"]
  FC --> FS["feedback.jsonl<br/>状态=PENDING_REVIEW"]
  FS --> BR["scripts/badcase_review.py<br/>Owner 人工裁定"]
  BR -->|approve| GC["golden-candidates.json<br/>候选区"]
  BR -->|reject| OUT["归档不回流"]
  GC --> REVIEW["人审定级<br/>（候选区纪律：未经人审不进集合）"]
  REVIEW --> GS["黄金集（检索/生成）"]
  GS --> EVAL["eval 跑批<br/>分数报告"]
  EVAL -.->|"分数变化反哺决策"| OPT["优化项立案<br/>（下次课程周）"]
```

**关键坐标**：反馈入集必过人（W24 裁决②A）；候选区与正式集物理隔离；
真实 badcase 694fcbe6 已走通全程（W24 #3）。

## 纵切④：eval 离线评估链路（尺子自身的生产线）

```mermaid
flowchart LR
  GS["黄金集<br/>（版本化 JSON）"] --> RUN["RetrievalEvalRunner /<br/>GenerationEvalRunner"]
  RUN --> SYS["被测通路<br/>（与线上同配置）"]
  SYS --> J["LlmEvalJudge<br/>（物理隔离，ADR-0014）"]
  J --> WS["校准工作表<br/>（双盲）"]
  WS --> HUMAN["Owner 人工打分"]
  HUMAN --> ANA["judge_calibration_analysis.py<br/>一致率分析"]
  ANA -->|一致率 ≥80%| PASS["judge 分可信，跑分有效"]
  ANA -->|不达标| FIX["立案修尺：输入侧注入/rubric 冻结<br/>（W20-W24 三次实践）"]
  PASS --> RPT["eval 报告入库 docs/eval/"]
```

**关键坐标**：judge 与被测通路物理隔离（不共享记忆/配置）；校准证据有保质期——
语料轮换或缺陷修复后必须复测（retro #3/#4，规范第五条）。

## 四条链路的共同模式

每条纵切都遵循同一个骨架：**入口留痕（traceId）→ 域内计算 → 出口校验 → 留痕回流**。
差异只在域内计算的形态（一次性检索 / 多轮循环 / 文件异步 / 离线跑批）。
新链路设计时照此骨架自检：入口有没有 traceId？出口有没有校验？留痕写到哪？
