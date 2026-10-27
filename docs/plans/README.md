# 作战计划索引

> 源头：《Agent 工程化实战路线（完整版）.pptx》（27 周：13 周实战 + 14 周进阶）。
> 本目录是按实际执行校准后的落地版——每周开工前细化当周计划，收官后补录回顾。
>
> - 蓝图蒸馏版（仓库内单一事实源）：[blueprint/README.md](blueprint/README.md)
> - 里程碑规划方法论（新会话凭此制定后续计划）：[planning-playbook.md](planning-playbook.md)

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
| 阶段索引 | [m3/README.md](m3/README.md)（W9-5 裁决：ReAct→查询改写→MCP Client→混合检索全纳入） | ✅ 已完成 |
| **M3 验收记录** | [m3/m3-acceptance.md](m3/m3-acceptance.md) | ✅ 五门四过一警示（2026-09-18，欠账两条入 W14） |

## 进阶期（W14-27）

| 周 | 文档 | 状态 |
|---|---|---|
| 阶段索引 | [advanced/README.md](advanced/README.md)（三份台账合并裁决校准版，2026-09-18） | 🔵 进行中 |
| W14 | [advanced/w14-retro-and-debt.md](advanced/w14-retro-and-debt.md)（复盘 + 欠账/缺陷清理） | ✅ 已完成 |
| W15 | [advanced/w15-embedding-compare.md](advanced/w15-embedding-compare.md)（embedding 实测对照） | ✅ 已完成 |
| W16 | [advanced/w16-sparse-bm25-eval.md](advanced/w16-sparse-bm25-eval.md)（sparse/BM25 候选 A 评估） | ✅ 已完成 |
| W17 | [advanced/w17-tushare-and-eval-data.md](advanced/w17-tushare-and-eval-data.md)（Tushare 真源 + 评估造数） | ✅ 已完成 |
| W18 | [advanced/w18-eval-data-week2.md](advanced/w18-eval-data-week2.md)（评估造数第二周 + judge 校准 95.5%） | ✅ 已完成 |
| W19 | [advanced/w19-rotation-first-run.md](advanced/w19-rotation-first-run.md)（黄金集轮换首演 v5/v3） | ✅ 已完成 |
| W20 | [advanced/w20-leak-fix-and-advisor-source.md](advanced/w20-leak-fix-and-advisor-source.md)（泄漏修复 + 校准重生 + Advisor 源码精读） | ✅ 已完成（#4 Temporal 裁决顺延 W21） |
| W21 | [advanced/w21-temporal-and-l4-kickoff.md](advanced/w21-temporal-and-l4-kickoff.md)（Temporal 顺延包 + judge 病灶处置 + L4 设计启动） | ✅ 已完成（Temporal 三件套 + 记忆 L2 MySQL + 校准复测 83.3% 过线） |
| W22 | [advanced/w22-l4-subagent-pipeline-and-skill.md](advanced/w22-l4-subagent-pipeline-and-skill.md)（三角色管线动工 + Skill 机制实测） | ✅ 已完成（管线切默认引擎；Skill 止步登记） |
| W23 | [advanced/w23-hitl-and-retrieval-diversity.md](advanced/w23-hitl-and-retrieval-diversity.md)（HITL 接通 + 检索主体多样性 + A2A 认知 + Skill 复测） | ✅ 已完成（L4 全项验收收官） |
| W24 | [advanced/w24-recall-robustness-and-feedback-loop.md](advanced/w24-recall-robustness-and-feedback-loop.md)（零召回防线 + judge 拒答口径 + 人工反馈回路 + 挂账清仓） | ✅ 已完成（四项全清，2026-10-27 收官） |
| W25 | [advanced/w25-cost-engineering-and-bm25-revisit.md](advanced/w25-cost-engineering-and-bm25-revisit.md)（成本仪表盘+预算熔断 + 模型路由 + bm25 重评 + 反馈最后一公里） | 🔵 已立案（Owner 裁决①B②A③数据裁决；24h 运行顺延 W26） |

## 对账校准记录（2026-09-03）

实战 W1-W3 后与 PPT 全量对账，结论：

- **遗漏 4 项**：L1 Prompt 工程整层（🔴）、模型选型笔记、Token 账单、PDF/元数据摄入 → 已编入 W4/W5
- **超前 3 项**：Minikube 部署（原 W12）、eval 雏形（原 W8）、OpenAPI/对话页（PPT 无）
- **风险 1 项**：M1 验收门（20 条黄金集 Recall@5 ≥ 0.7）未正式过 → W5-Step3 必过，不过不进 W6
