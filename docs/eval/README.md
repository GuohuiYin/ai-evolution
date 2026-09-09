# 评估报告归档

> 一切 eval 产出的归档处：**没有 eval 的 RAG 是裸奔**。
> 报告模板约定（W8 起）：实测记录（问了什么 / 实际结果 / 预期）先行，判读在后。

## 现有资产

- `src/main/resources/eval/golden-set.json`：检索黄金集（39 条：normal 15 + boundary 6 + paraphrase 4 + literal 4 + adversarial 10）
- `RetrievalEvaluator` + `RetrievalEvalRunner`：`AI_EVAL_ENABLED=true` 触发评测（真实 bge-m3）
- CI 回归：`GoldenRetrievalEvalIT`（哈希向量替身，只覆盖 normal 正例）

## 报告清单

| 时点 | 报告 | 一句话结论 |
|---|---|---|
| W4 | [p0-prompt-comparison.md](p0-prompt-comparison.md) | CO-STAR + few-shot 在内容保真度上全面优于裸指令；格式由代码保证 |
| W5 | [m1-gate-recall.md](m1-gate-recall.md) | Recall@5 85.7% 过 M1 门；多源语料逼出 expectSources 语义升级 |
| W8 | [w8-retrieval-baseline.md](w8-retrieval-baseline.md) | 39 条基线 Recall@5 72%；混合检索获 5 条量化证据（M3 候选） |

## 待落地（W8 进行中）

- 生成层黄金集（含 in-domain-unanswerable + 红线遵循）+ LLM-as-a-Judge 三维评分
- RAG few-shot 对照实验（挂在生成评估上裁决）
- 一页成本账
