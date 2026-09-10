# 评估体系

> 一切 eval 产出的归档处：**没有 eval 的 RAG 是裸奔**。
> 报告模板约定（W8 起）：实测记录（问了什么 / 实际结果 / 预期）先行，判读在后。

## 分层框架（W9 定稿）

两个维度 × 四个评估域：

- **维度一·时效**：一次性**选型评测**（决策时点做一次，产出结论进 ADR）vs 持续**质量回归门**（每次改动可重跑，防退化）
- **维度二·评估域**：检索质量 / 生成质量 / 红线安全 / 成本

元层：judge 锚定是"评估评估者"（metaeval）——没有它，生成质量的分数本身不可信。

## 目录结构

### [selection/](selection/) — 一次性选型评测

| 报告 | 结论 |
|---|---|
| [model-selection-v1.md](selection/model-selection-v1.md) | DeepSeek vs Qwen 双模型 10 问实测（能力/延迟/token 三维）→ DeepSeek 主力、Qwen 备选 |
| [embedding-selection-v1.md](selection/embedding-selection-v1.md) | 调研论证型（W9）：维持 BGE-M3；实测对照列进阶阶段（W14+） |
| [vectorstore-selection-v1.md](selection/vectorstore-selection-v1.md) | 调研论证型（W9）：维持 Qdrant——本项目量级下选型由运维成本与生态决定；含重评触发条件 |
| [model-selection-samples/](selection/model-selection-samples/) | 模型实测的原始响应样本与延迟记录 |

> 选型方法约定：默认调研论证（需求矩阵 + 公开 benchmark + 显式约束），
> 只有结论可能被数据推翻时才做实测（如对话模型对比）。embedding 实测对照已列 W14+ 进阶清单。

### [retrieval/](retrieval/) — 检索质量回归

黄金集 39 条（normal 15 + boundary 6 + paraphrase 4 + literal 4 + adversarial 10），
`AI_EVAL_ENABLED=true ./mvnw spring-boot:run` 触发（真实 bge-m3）；
CI 侧 `GoldenRetrievalEvalIT` 用哈希向量替身只覆盖 normal 正例。

| 时点 | 报告 | 一句话结论 |
|---|---|---|
| W5 | [m1-gate-recall.md](retrieval/m1-gate-recall.md) | Recall@5 85.7% 过 M1 门；多源语料逼出 expectSources 语义升级 |
| W8 | [w8-retrieval-baseline.md](retrieval/w8-retrieval-baseline.md) | 39 条基线 Recall@5 72%；混合检索获 5 条量化证据（M3 候选） |

### [generation/](generation/) — 生成质量回归

黄金集 16 条（redline / in-domain-unanswerable / factual / compound 四类），
LLM-as-a-Judge 三维评分（准确性 / 拒答正确性 / 溯源），judge 锚定 5/5；
`AI_EVAL_GENERATION_ENABLED=true ./mvnw spring-boot:run` 触发。

| 时点 | 报告 | 一句话结论 |
|---|---|---|
| W4 | [p0-prompt-comparison.md](generation/p0-prompt-comparison.md) | CO-STAR + few-shot 在内容保真度上全面优于裸指令；格式由代码保证（[原始样本](generation/p0-samples/)） |
| W8 | [w8-generation-eval.md](generation/w8-generation-eval.md) | 总体 5.3/6；冰淇淋案例暴露"部分相关数据缝合"漏洞 → W9-2 agent-chat-v2 反缝合规则修复后恢复 2/2/2 |

### 红线安全 — 单列的评估域

红线失败的代价与 factual 错误完全不同，金融场景下单列管理：

- 用例库：[docs/security/w7-redteam-baseline.md](../security/w7-redteam-baseline.md)（红队 10 用例）
- 回归出口：生成黄金集 `redline` 类别（见上方生成报告，W8 起 3/3 满分）

### 成本 — 独立评估域

成本影响选型（缓存命中、模型档位），不只是质量指标：
[docs/cost/w8-cost-report.md](../cost/w8-cost-report.md)（单次问答 ¥0.0054，一轮生成 eval ¥0.17，一页制随里程碑更新）

## 演进路线

- W11：生成 eval 接入 CI 定时回归门（GitHub Actions），四域从"手动跑"升级为"自动门"
- M3 候选裁决证据积累中：混合检索（5 条）、查询改写（3 条）
