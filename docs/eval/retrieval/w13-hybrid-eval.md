# W13 混合检索数据裁决报告（dense + sparse + RRF）

> 2026-09-17 本地跑批，真实 bge-m3，黄金集 v3（39 条）不变，Recall@5 / 阈值 0.5 / topK=5。
> 裁决口径（ADR-0017）：hybrid 开 vs 关，Recall@5 提升 ≥5pp 才默认启用。
> 设计稿 [ADR-0017](../../adr/0017-retrieval-two-stage-design.md)；chunk-size 前置实验
> [w13-chunk-size-comparison](w13-chunk-size-comparison.md)。

## 裁决结果：✅ 启用（+21pp，远超 5pp 达标线）

| 轮次 | 正例 Recall@5 | 负例拒答率 | M1 门 |
|---|---|---|---|
| hybrid 关（规整化语料 dense 基线） | 21/29（72%） | 7/10 | ✅ |
| **hybrid 开（dense+sparse+RRF，k=60，recall-top-k=20）** | **27/29（93%）** | **7/10** | ✅ |

**+6 条翻转，零误伤**：adversarial 7/10 两轮一致（sparse 覆盖率裁决精度优先，近域陷阱未被字面路勾出）。

## 分类别对比

| 类别 | hybrid 关 | hybrid 开 | 变化 |
|---|---|---|---|
| normal | 14/15 | **15/15** | +1（`12987 工艺的具体含义`） |
| paraphrase | 2/4 | **4/4** | +2（`12987是什么` / `茅台工艺里 12987 每个数字…`） |
| literal | 2/4 | **4/4** | +2（`赤水河流域环境容量约束` / `SNE Research 口径 37%`） |
| boundary | 3/6 | 4/6 | +1（`碳酸锂价格波动会影响谁`） |
| adversarial | 7/10 | 7/10 | 0（三条语义近域为固有边界，W8 已判读） |

**W8 三大失败模式全部击穿**：数字代号（模式一）、原文片段（模式二）由 sparse 路解决；
指代消解（模式三，boundary 剩 2 条）属 rerank/改写层靶子，留给 #3。

## 参数敏感性（ADR-0017 收口项）

| 参数 | 档位 | Recall@5 | 结论 |
|---|---|---|---|
| rrf-k | 30 / 60 / 100 | 均 27/29（93%） | 差异 <1pp，锁经典值 60 不再议 |
| recall-top-k | 20 / 40 | 均 27/29（93%） | 小语料适配定稿 20（非对标业界 50-200） |

## 实施期实证发现的三个分词器/匹配坑（probe 驱动，均入代码注释）

1. **multilingual 分词器粘连数字与中文**："12987流程" 切为单 token，代号查询永不命中。
   解法：摄入/查询双侧规整化（`FullTextNormalizer`，粘连处插空格，作用于文本本身不留孪生字段；
   规整化版本并入 manifest 判变键）。
2. **MatchText 是严格 AND**：自然语言问句一词不在文中即零命中。解法：MatchText 降级为
   候选网（标识符整 token + CJK 二元组 should 宽捞），命中裁决移至应用侧
   `SparseCoverage`（代号全中方放行 / 假代号一律拒 / 纯中文二元组覆盖率 ≥0.5）。
3. **纯数字单 token 查询被分词器丢弃 + 高频词洪泛**："茅台" 二元组单条件即可占满候选网上限，
   把含代号的块挤出捞网。解法：标识符兜底——网空收时按 must 条件全量扫（小语料适配，
   语料上量须随 ADR 候选 A 触发条件一起重评）。

已知残余限制（诚实登记）：孤立纯数字单 token 查询（如只发 "12987"）仍不中——
黄金集无此用例；若成为真实病灶，升级路径是 ADR-0017 sparse 候选 A（真 BM25 稀疏向量）。

## 成本账

- 每查询新增：sparse scroll 1 次（偶发兜底 2 次），本地实测毫秒级，无 embedding/LLM 计费
- 一次性：全文索引建立（启动即建，幂等）；规整化触发过一次全量重摄入（303 块）
- 三轮敏感性 + 两轮对照共 5 次全量回归：5×39 条查询 embedding，无其他调用

## 复跑方式

```bash
# 基线（hybrid 关）
AI_EVAL_ENABLED=true ./mvnw spring-boot:run
# 裁决组（hybrid 开，现默认）
AI_EVAL_ENABLED=true AI_RAG_HYBRID_ENABLED=true ./mvnw spring-boot:run
# 敏感性：AI_RAG_HYBRID_RRF_K=30|100 / AI_RAG_RECALL_TOP_K=40
```

> 注：规整化语料 dense 基线 72% 与规整化前（W13 #1 三轮）完全一致——
> 规整化对 dense 召回零扰动，93% 的提升全部归属 sparse 路 + RRF。
