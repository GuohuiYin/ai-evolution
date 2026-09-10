# 作战计划索引

> 源头：《Agent 工程化实战路线（完整版）.pptx》（27 周：13 周实战 + 14 周进阶）。
> 本目录是按实际执行校准后的落地版——每周开工前细化当周计划，收官后补录回顾。

## M1 地基 + RAG（W1-W5）

| 周 | 文档 | 状态 |
|---|---|---|
| W1 工程地基与云原生基座 | [w1-foundation.md](m1/w1-foundation.md) | ✅ 已完成（含偏离记录） |
| W2 真实模型接入与工程化配套 | [w2-real-model.md](m1/w2-real-model.md) | ✅ 已完成（L1 欠账→W4） |
| W3 RAG 检索增强全链路 | [w3-rag.md](m1/w3-rag.md) | ✅ 已完成（M1 验收门→W5） |
| W4 L1 Prompt 工程补课 | [w4-l1-prompt-engineering.md](m1/w4-l1-prompt-engineering.md) | ✅ 已完成 |
| W5 Function Calling + M1 验收门 | [w5-function-calling.md](m1/w5-function-calling.md) | ✅ 已完成 |
| **M1 验收记录** | [m1-acceptance.md](m1/m1-acceptance.md) | ✅ Recall@5 86.7%（2026-09-05，形态补记） |

## M2 Harness + Eval（W6-W9）

| 周 | 文档 | 状态 |
|---|---|---|
| 阶段索引 | [m2/README.md](m2/README.md)（含 W6/W7/W8/W9 各周独立文档） | ✅ 已完成 |
| **M2 验收记录** | [m2-acceptance.md](m2/m2-acceptance.md) | ✅ 三件全过（2026-09-09） |

## M3 最小 Loop（W10-W13）

| 周 | 文档 | 状态 |
|---|---|---|
| 阶段索引 | [m3/README.md](m3/README.md)（每周计划开工时独立成文） | 📋 锚定范围 |

## 对账校准记录（2026-09-03）

实战 W1-W3 后与 PPT 全量对账，结论：

- **遗漏 4 项**：L1 Prompt 工程整层（🔴）、模型选型笔记、Token 账单、PDF/元数据摄入 → 已编入 W4/W5
- **超前 3 项**：Minikube 部署（原 W12）、eval 雏形（原 W8）、OpenAPI/对话页（PPT 无）
- **风险 1 项**：M1 验收门（20 条黄金集 Recall@5 ≥ 0.7）未正式过 → W5-Step3 必过，不过不进 W6
