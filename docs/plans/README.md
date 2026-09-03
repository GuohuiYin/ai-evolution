# 作战计划索引

> 源头：《Agent 工程化实战路线（完整版）.pptx》（27 周：13 周实战 + 14 周进阶）。
> 本目录是按实际执行校准后的落地版——每周开工前细化当周计划，收官后补录回顾。

## M1 地基 + RAG（W1-W5）

| 周 | 文档 | 状态 |
|---|---|---|
| W1 工程地基与云原生基座 | [w1-foundation.md](w1-foundation.md) | ✅ 已完成（含偏离记录） |
| W2 真实模型接入与工程化配套 | [w2-real-model.md](w2-real-model.md) | ✅ 已完成（L1 欠账→W4） |
| W3 RAG 检索增强全链路 | [w3-rag.md](w3-rag.md) | ✅ 已完成（M1 验收门→W5） |
| W4 L1 Prompt 工程补课 | [w4-l1-prompt-engineering.md](w4-l1-prompt-engineering.md) | 📋 待开工 |
| W5 Function Calling + M1 验收门 | [w5-function-calling.md](w5-function-calling.md) | 📋 草案 |

## M2 Harness + Eval（W6-W9）

| 周 | 文档 | 状态 |
|---|---|---|
| W6-W9 概要 | [w6-w9-m2-harness-eval.md](w6-w9-m2-harness-eval.md) | 📋 锚定范围 |

## M3 最小 Loop（W10-W13）

| 周 | 文档 | 状态 |
|---|---|---|
| W10-W13 概要 | [w10-w13-m3-minimal-loop.md](w10-w13-m3-minimal-loop.md) | 📋 锚定范围 |

## 对账校准记录（2026-09-03）

实战 W1-W3 后与 PPT 全量对账，结论：

- **遗漏 4 项**：L1 Prompt 工程整层（🔴）、模型选型笔记、Token 账单、PDF/元数据摄入 → 已编入 W4/W5
- **超前 3 项**：Minikube 部署（原 W12）、eval 雏形（原 W8）、OpenAPI/对话页（PPT 无）
- **风险 1 项**：M1 验收门（20 条黄金集 Recall@5 ≥ 0.7）未正式过 → W5-Step3 必过，不过不进 W6
