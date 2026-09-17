# W13 Reranking 精排数据裁决报告

> 2026-09-17 本地跑批，真实 bge-m3 + bge-reranker-v2-m3（SiliconFlow），黄金集 v3（39 条）不变，
> hybrid 全程开（当前生产形态）。裁决口径（ADR-0017）：Recall@5 提升 ≥5pp 才默认启用。
> 前置：[w13-hybrid-eval](w13-hybrid-eval.md)（hybrid 72%→93%）；成本账
> [reranker-selection-v1](../selection/reranker-selection-v1.md)。

## 裁决结果：⛔ 不启用（+0pp，远低于 5pp 启用线）

| 轮次 | 正例 Recall@5 | 负例拒答率 | 失败集合 |
|---|---|---|---|
| hybrid 开 + rerank 关（生产现状） | 27/29（93%） | 7/10 | 5 条 |
| hybrid 开 + rerank 开 | 27/29（93%） | 7/10 | **完全相同的 5 条** |

rerank 保持默认关（`ai.rag.rerank.enabled: false`），实现与配置保留——
管线能力落地，启用与否由数据说了算（同 few-shot/hybrid 裁决机制）。

## 为什么 +0pp：rerank 治不了"没捞回来"

剩余 5 条失败的召回段实况（rerank 开轮 `stage=RETRIEVE` 日志）：

| 用例 | 类别 | 召回段 hits | 判读 |
|---|---|---|---|
| 这两家公司里谁的品牌壁垒更厚 | boundary | 0 | 指代消解问题，召回段零产出 |
| 谁的风险和原材料价格关系最大 | boundary | 0 | 同上 |
| 比亚迪刀片电池的技术参数 | adversarial | 误召回 catl.md | 语义近域，rerank 不设阈值滤不掉 |
| 宁德时代的股票现在值得买入吗 | adversarial | 误召回 catl.md | 同上 |
| 五粮液的营收规模有多大 | adversarial | 误召回茅台年报 | 同上 |

rerank 的靶子是"捞回来排错位/压线误杀"——但当前黄金集的残余失败全部是
**召回段零产出**（boundary 指代）或**语义近域误召回**（adversarial 固有边界，W8 已判读），
两类都不在 rerank 能力半径内。排序池里没有可救的候选，+0pp 是结构性结果而非偶然。

**重评触发条件**（入 reranker-selection-v1 一页账）：召回侧格局变化（sparse 升级候选 A /
语料上量）、黄金集扩入排名类用例、或线上出现"排错位"实证——任一成立即重跑本对照。

## 本轮顺带验证的管线行为（单测之外的实跑证据）

- 零召回短路：9 条零召回查询未打 rerank 计费电话（`rerank=skipped` 日志 × 9）
- 30 次真实 rerank 调用零故障零降级（降级路径由单测锁定：`rerankFailureDegradesToRecallPoolCappedAtTopN`）
- usage 逐条留痕：103,770 input tokens，一页账汇总口径验证可行
- `Document.score` 语义切换为 reranker 分数（stage=RETRIEVE 日志 rerankTopScore 段）

## 复跑方式

```bash
# rerank 开（hybrid 现为默认开）
AI_EVAL_ENABLED=true AI_RAG_RERANK_ENABLED=true ./mvnw spring-boot:run
```
