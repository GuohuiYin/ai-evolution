# W11 生成层评估：显式 ReAct 循环切换回归报告

> 2026-09-14 本地真实跑批两轮（DeepSeek 生成 + DeepSeek judge 独立调用），黄金集 16 条不变。
> 背景：W11 #3c `AgentChatService` 从 Spring AI 隐式工具循环切换到显式 ResearchLoop（ADR-0015）。
> 验收口径：现有 16 条生成黄金集回归不降级（对照 [W8 基线](w8-generation-eval.md) 总体 5.3/6）。

## 两轮跑批对照

| 类别 | W8 基线 | 首轮（切换后） | 修复后复跑 |
|---|---|---|---|
| redline（3 条） | 6.0/6 | 6.0/6 | 6.0/6 |
| compound（3 条） | 5.7/6 | 3.3/6 ↓↓ | 6.0/6 ↑ |
| in-domain-unanswerable（4 条） | 5.0/6 | 3.3/6 ↓ | 6.0/6 ↑ |
| factual（6 条） | 4.8/6 | 5.3/6 ↑ | 5.0/6 ↑ |
| **总体** | **5.3/6** | **4.6/6 ↓** | **5.6/6 ✅ 不降级** |

## 首轮降级的根因（本报告最有价值的部分）

首轮 6 条 Agent 路由用例中 4 条的"回答"是协议原文（`Thought: ... Action: tool({...})`）——
模型把入参**内联进 Action 行**（`Action: getFinancialSummary({"code":"000858",...})`），
解析器只认独立 `Action Input:` 行 → 不合协议 → 直通兜底把原文当终答。

**教训两条**：

1. 静默降级是故障放大器——兜底逻辑把协议违背翻译成了"看似正常的烂答案"， 没有 eval 跑批根本不会暴露。修复时同步给直通兜底加了 `protocol violation` warn 日志（降级必须可见）
2. 文本协议的格式漂移是常态，解析器容差是一等职责——修复后第二轮 protocol violation 0 次

## 复跑判读

- compound / unanswerable 两类**超过** W8 基线：显式循环的分步查证让复合问题"每个子问题都有工具动作"，不可答问题"工具确认未覆盖后如实收尾"——隐式循环时代这两种行为靠模型自觉
- factual 唯一失分仍是 `12987 每个数字代表什么`（2/6）：检索层数字盲区传导，根因修复在 W13 混合检索，与本次切换无关
- 红线三类全满分：显式协议没有削弱合规护栏

## 复现

```bash
set -a && source .env && set +a
AI_EVAL_GENERATION_ENABLED=true ./mvnw spring-boot:run
# 报告在启动日志：GenerationEvalRunner「生成 eval 报告」段
```
