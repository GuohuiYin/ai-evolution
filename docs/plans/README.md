# 作战计划索引

> 源头：《Agent 工程化实战路线（完整版）.pptx》（27 周：13 周实战 + 14 周进阶）。
> 本目录是按实际执行校准后的落地版——每周开工前细化当周计划，收官后补录回顾。

## M1 地基 + RAG（W1-W5）

| 周 | 文档 | 状态 |
|---|---|---|
| 阶段索引 | [m1/README.md](m1/README.md)（含 W1-W5 各周独立文档） | ✅ 已完成 |
| **M1 验收记录** | [m1-acceptance.md](m1/m1-acceptance.md) | ✅ Recall@5 86.7%（2026-09-05，形态补记） |


## M2 Harness + Eval（W6-W9）

| 周 | 文档 | 状态 |
|---|---|---|
| 阶段索引 | [m2/README.md](m2/README.md)（含 W6/W7/W8/W9 各周独立文档） | ✅ 已完成 |
| **M2 验收记录** | [m2-acceptance.md](m2/m2-acceptance.md) | ✅ 三件全过（2026-09-09） |

## M3 最小研究 Loop（W10-W13）

| 周 | 文档 | 状态 |
|---|---|---|
| 阶段索引 | [m3/README.md](m3/README.md)（W9-5 裁决：ReAct→查询改写→MCP Client→混合检索全纳入） | 🔵 进行中 |
| W10 | [m3/w10-react-loop.md](m3/w10-react-loop.md) | 🔵 待开工 |

## 对账校准记录（2026-09-03）

实战 W1-W3 后与 PPT 全量对账，结论：

- **遗漏 4 项**：L1 Prompt 工程整层（🔴）、模型选型笔记、Token 账单、PDF/元数据摄入 → 已编入 W4/W5
- **超前 3 项**：Minikube 部署（原 W12）、eval 雏形（原 W8）、OpenAPI/对话页（PPT 无）
- **风险 1 项**：M1 验收门（20 条黄金集 Recall@5 ≥ 0.7）未正式过 → W5-Step3 必过，不过不进 W6
