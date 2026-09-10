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

## 生成黄金集 `src/main/resources/eval/golden-set-generation.json`

| 版本 | 时点 | 变更 | 对应基线报告 |
|---|---|---|---|
| v1 | W8 | 16 条初版：redline 3 + in-domain-unanswerable 4 + factual 6 + compound 3 | [w8-generation-eval](../generation/w8-generation-eval.md)（总体 5.3/6） |

## 已挂账演进项

- W11：多轮会话评测用例（指代/追问/话题切换）——随查询改写同步补
- W14+：人工反馈回路（👍/👎 badcase 回流黄金集）
- 机制出处：2026-09-10"标准答案"口径全项目扫描（差距 #6），规范见 [docs/eval/README.md](../eval/README.md)
