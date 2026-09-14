# ADR-0015：显式 ReAct 研究循环替换 Spring AI 隐式工具循环

- 状态：已接受
- 日期：2026-09-14（W11 #1-#3 落地）

## 背景

W5 起 Agent 通路的工具调用由 Spring AI 内部循环隐式完成（模型决定 → 框架循环 → 终答）：轨迹不可见、步数上限只能靠框架级 `max-total-tool-calls` 兜底、中途状态不可观测。M3 主线要求"提问→检索→综合→再提问"的多步研究循环，循环必须先拿到明面上（Harness Correct 域的系统化起点）；轨迹可观测（SSE 推送、对话页展示）与轨迹评估（W13 验收门）都以显式循环为前提。

## 决策

1. **自研串行骨架 `ResearchLoop`（loop 子域）**：显式迭代 thought/action/observation，`maxSteps` 上限优雅退出（answer=null 不编造，措辞归调用方）。不引入新框架，Spring AI 只做模型原语供给。
2. **ReAct 文本协议而非原生 function calling**：模型输出 `Thought/Action/Action Input/ Final Answer` 文本，由 `ReactProtocolParser` 解析。显式循环需要"每步暂停、由 Harness 决定执行"，原生 FC 的框架循环恰恰是要被替换的东西；文本协议还天然产出可读的 thought 轨迹。
3. **IOP 双端口**：`ResearchModel`（模型侧）/ `ToolExecutor`（工具侧）+ `ToolRegistry` 按名白名单派发（无反射，呼应 W8-1 工具解析安全立场）+ `LoopListener` 观测端口（SSE 推送与轨迹评估共用）。
4. **降级哲学**：不可派发（未知工具/缺参/JSON 畸形）降级为错误观察喂回模型自我纠正；不合协议的输出直通兜底为终答，但必须 `log.warn` 可见——首轮 eval 证明静默降级会掩盖故障（三案归零）。
5. **会话记忆改手工读写**：原 `MessageChatMemoryAdvisor` 会在循环每步重复追加同一条 user 消息；改为跑前取窗口历史注入、跑完写回本轮问答。

## 备选方案

- 继续用 Spring AI 内部循环 + 拦截器观测：轨迹只能从 ToolCallback 侧反推，thought 不可得，步数控制依赖框架内部行为。否决。
- 引入 LangGraph4j 等图编排框架：重型依赖换编排能力，当前串行场景用不上；W14+ 并行编排时重评。否决（周计划"明确不做"同旨）。
- 原生 function calling + 逐步手动续跑：每条 FC 响应都需手工构造 tool 消息回填，协议细节绑死供应商实现差异；文本协议更简单可移植。否决。

## 后果

- 正：黄金集回归 5.6/6 ≥ W8 基线 5.3/6（compound 5.7→6.0、unanswerable 5.0→6.0、factual 4.8→5.0、redline 持平 6.0）；轨迹全程日志可还原 + SSE 实时推送；研究行为第一次有了可评估的对象。
- 正（意外收获）：格式漂移容差成为 parser 的一等职责——首轮 eval 实锤"Action 行内联 JSON"漂移，解析器当天长出对应容差用例。
- 负/权衡：Agent 通路终答不再逐字流式（ReAct 产品形态：逐步轨迹 + 一次性结论）；协议遵从度是新的质量维度，需 eval 持续度量；文本协议 token 开销略高于原生 FC（thought 显式化是目的本身，不视为浪费）。
- 待办：轨迹评估指标（步数/冗余调用/收敛性）W13 进 M3 验收门；并行工具编排 W14+。
