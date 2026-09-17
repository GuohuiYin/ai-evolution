# 黄金集版本台账

> 目的：评估结果可复现——报告中的每个分数必须能对应到数据集版本。
> 机制：数据集 JSON 为顶层数组结构（加载器强约束，不内嵌版本字段），
> 版本演进以本台账 + git 历史为准；**每次增删改数据集，必须先在此登记再提交**。

## 检索黄金集 `src/main/resources/eval/golden-set.json`

| 版本 | 时点 | 变更 | 对应基线报告 |
|---|---|---|---|
| v1 | W5（M1 验收门） | 20 条初版 | [m1-gate-recall](../retrieval/m1-gate-recall.md)（Recall@5 85.7%） |
| v2 | W5 | PDF 年报入库后 expectSources 语义升级（多源） | 同上（复跑 86.7%） |
| v3 | W8 | 扩充至 39 条：normal 15 + boundary 6 + paraphrase 4 + literal 4 + adversarial 10 | [w8-retrieval-baseline](../retrieval/w8-retrieval-baseline.md)（Recall@5 72%） |

v3 数据集下的对照实验档案（数据集不变，参数回归）：

| 时点 | 实验 | 结论 | 报告 |
|---|---|---|---|
| W13（2026-09-17） | chunk-size 400/800/1200 三轮全量回归 | 三轮 Recall@5 均 21/29（72%），维持 800 | [w13-chunk-size-comparison](../retrieval/w13-chunk-size-comparison.md) |
| W13（2026-09-17） | 混合检索 on/off 对照 + 参数敏感性 | Recall@5 72%→93%（+21pp 达标），hybrid 默认启用；rrf-k 锁 60、recall-top-k 定稿 20 | [w13-hybrid-eval](../retrieval/w13-hybrid-eval.md) |
| W13（2026-09-17） | rerank on/off 对照（hybrid 全程开） | 均 27/29（93%），+0pp 不达标——rerank 保持默认关，重评触发条件在案 | [w13-rerank-eval](../retrieval/w13-rerank-eval.md) |

## 生成黄金集 `src/main/resources/eval/golden-set-generation.json`

| 版本 | 时点 | 变更 | 对应基线报告 |
|---|---|---|---|
| v1 | W8 | 16 条初版：redline 3 + in-domain-unanswerable 4 + factual 6 + compound 3 | [w8-generation-eval](../generation/w8-generation-eval.md)（总体 5.3/6） |
| v1.1 | W11（2026-09-14） | 新增 multi-turn 6 条（指代消解 3 + 追问深化 1 + 话题切换 1 + 多轮红线 1），用例 schema 增 `turns` 字段（单轮用例不变，向后兼容）；16→22 条 | [w11-multi-turn](../generation/w11-multi-turn.md)（改写开/关两轮对比） |

## 已挂账演进项

- W11：多轮会话评测用例（指代/追问/话题切换）——随查询改写同步补
- W14+：人工反馈回路（👍/👎 badcase 回流黄金集）
- 机制出处：2026-09-10"标准答案"口径全项目扫描（差距 #6），规范见 [docs/eval/README.md](../eval/README.md)
