# 评估报告归档

> 一切 eval 产出的归档处：**没有 eval 的 RAG 是裸奔**。

## 现有资产

- `src/main/resources/eval/golden-set.json`：检索黄金集雏形（6 条：4 正 2 负）
- `RetrievalEvaluator` + `RetrievalEvalRunner`：`AI_EVAL_ENABLED=true` 触发评测

## 归档计划

| 时点 | 报告 |
|---|---|
| W4 | P0 prompt 改前/改后效果对比（L1 退出信号实证） |
| W5 | **M1 验收门**：黄金集扩至 20 条，Recall@5 ≥ 0.7 |
| W8 | P4-lite：30-50 条黄金集，一键评分（准确性+遵循度+溯源完整性） |
