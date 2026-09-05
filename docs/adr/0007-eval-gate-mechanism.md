# ADR-0007：Eval 验收门机制（黄金集 + Recall@K + 不过不进下一阶段）

- 状态：已接受
- 日期：2026-09-05（M1 验收门正式过堂当日定稿）

## 背景

Agent 工程的传统单测无法回答"检索够不够好"；LLM 相关逻辑需要 eval 作为回归测试形态（约定 O4）。需要把"评估"从临时动作固化为机制。

## 决策

1. **黄金集三类别分层**：`normal`（直给事实）/ `boundary`（改写与间接问法）/ `adversarial`（域外与陷阱问题，期望不召回）。分类防止边界用例的失败被总数稀释。
2. **指标为 Recall@K，评估口径 = 线上口径**：evaluator 与线上服务共用同一 `KnowledgeRetriever` 与 `ai.rag.*` 配置，避免"评的不是用的"。
3. **验收门机制**：每个里程碑设量化门槛（M1：Recall@5 ≥ `ai.eval.gate-threshold`，默认 0.7），不过门不进入下一阶段——铁律。
4. **门槛为何是 0.7 而非 0.9**：知识库仅 2 篇示例文档的冷启动阶段，0.7 验证"链路基本正确"即可；门槛随语料增长逐里程碑上调，而非起步追求完美。
5. **CI / 本地分工**：CI 用确定性哈希向量替身（`TinyHashEmbeddingModel`）只跑 normal 正例守链路；boundary/adversarial 的语义判定依赖真实 embedding 距离分布，由本地真实 bge-m3 验收承担——替身跑语义题必然假阳性/假阴性。

## 备选方案

- 全量黄金集跑 CI：真实 embedding API 入 CI 引入密钥管理与网络依赖，且成本/耗时不可接受。否决。
- 无门槛凭感觉：等于没有 eval。否决。

## 后果

- 正：M1 验收门（Recall@5=85.7%）为首份量化证据，归档 `docs/eval/m1-gate-recall.md`；调参有了回归基线。
- 待办：负例集分层重构（`out-of-domain` vs `in-domain-unanswerable`）列入 W6；黄金集随语料扩容增长至 30-50 条（W8）。
