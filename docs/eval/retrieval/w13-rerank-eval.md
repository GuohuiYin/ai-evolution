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

## 逐条对比数据（39 条全量，两轮日志逐行对齐）

汇总：**39 条中 36 条两轮完全一致；pass/fail 零翻转；3 条两轮都通过但 Top1 排名变化**
（均为茅台年报 PDF → maotai.md，见下文"排名变化的含义"）。

> Top1 列别名：`茅台年报.pdf` = `贵州茅台：贵州茅台2024年年度报告.pdf`；
> `未召回` = 召回段零产出（负例场景的期望行为）。

### normal（15 条，两轮均 15/15）

| 查询 | rerank 关 结果 | rerank 关 Top1 | rerank 开 结果 | rerank 开 Top1 | 差异 |
|---|---|---|---|---|---|
| 12987 工艺的具体含义 | ✅ | maotai.md | ✅ | maotai.md | — |
| 储能电池业务的增长情况 | ✅ | catl.md | ✅ | catl.md | — |
| 宁德时代 2024 年归母净利润 | ✅ | catl.md | ✅ | catl.md | — |
| 宁德时代 2024 年营业总收入 | ✅ | catl.md | ✅ | catl.md | — |
| 宁德时代主营什么电池 | ✅ | catl.md | ✅ | catl.md | — |
| 宁德时代的全球市占率 | ✅ | catl.md | ✅ | catl.md | — |
| 宁德时代的客户包括哪些整车厂 | ✅ | catl.md | ✅ | catl.md | — |
| 茅台 2024 年归母净利润约多少 | ✅ | 茅台年报.pdf | ✅ | maotai.md | **排名变** |
| 茅台一年的营收规模 | ✅ | 茅台年报.pdf | ✅ | 茅台年报.pdf | — |
| 茅台的核心产品是什么 | ✅ | maotai.md | ✅ | maotai.md | — |
| 茅台的毛利率是多少 | ✅ | 茅台年报.pdf | ✅ | 茅台年报.pdf | — |
| 茅台的酿造工艺是什么 | ✅ | maotai.md | ✅ | maotai.md | — |
| 贵州茅台现任董事长是谁 | ✅ | 茅台年报.pdf | ✅ | 茅台年报.pdf | — |
| 飞天茅台是哪个公司的产品 | ✅ | maotai.md | ✅ | maotai.md | — |
| 麒麟电池和神行超充电池是什么 | ✅ | catl.md | ✅ | catl.md | — |

### paraphrase（4 条，两轮均 4/4）

| 查询 | rerank 关 结果 | rerank 关 Top1 | rerank 开 结果 | rerank 开 Top1 | 差异 |
|---|---|---|---|---|---|
| 12987是什么 | ✅ | maotai.md | ✅ | maotai.md | — |
| 一年生产周期、两次投料、九次蒸煮说的是哪种酒 | ✅ | maotai.md | ✅ | maotai.md | — |
| 宁德时代动力电池占全球多大份额 | ✅ | catl.md | ✅ | catl.md | — |
| 茅台工艺里 12987 每个数字分别代表什么 | ✅ | maotai.md | ✅ | maotai.md | — |

### literal（4 条，两轮均 4/4）

| 查询 | rerank 关 结果 | rerank 关 Top1 | rerank 开 结果 | rerank 开 Top1 | 差异 |
|---|---|---|---|---|---|
| SNE Research 口径 37% | ✅ | catl.md | ✅ | catl.md | — |
| 两次投料 九次蒸煮 八次发酵 七次取酒 | ✅ | 茅台年报.pdf | ✅ | maotai.md | **排名变** |
| 神行超充电池 | ✅ | catl.md | ✅ | catl.md | — |
| 赤水河流域环境容量约束 | ✅ | maotai.md | ✅ | maotai.md | — |

### boundary（6 条，两轮均 4/6）

| 查询 | rerank 关 结果 | rerank 关 Top1 | rerank 开 结果 | rerank 开 Top1 | 差异 |
|---|---|---|---|---|---|
| 宁德时代的第二增长曲线是什么 | ✅ | catl.md | ✅ | catl.md | — |
| 碳酸锂价格波动会影响谁 | ✅ | catl.md | ✅ | catl.md | — |
| 茅台净利润和分红情况如何 | ✅ | 茅台年报.pdf | ✅ | 茅台年报.pdf | — |
| 茅台酒产能为什么难以扩张 | ✅ | 茅台年报.pdf | ✅ | maotai.md | **排名变** |
| 谁的风险和原材料价格关系最大 | ❌ | 未召回 | ❌ | 未召回 | — |
| 这两家公司里谁的品牌壁垒更厚 | ❌ | 未召回 | ❌ | 未召回 | — |

### adversarial（10 条，两轮均拒答 7/10）

| 查询 | rerank 关 结果 | rerank 关 Top1 | rerank 开 结果 | rerank 开 Top1 | 差异 |
|---|---|---|---|---|---|
| OpenAI 最新发布了什么模型 | ✅ | 未召回 | ✅ | 未召回 | — |
| 五粮液的营收规模有多大 | ❌ | 茅台年报.pdf | ❌ | 茅台年报.pdf | — |
| 今天天气怎么样 | ✅ | 未召回 | ✅ | 未召回 | — |
| 宁德时代的股票现在值得买入吗 | ❌ | catl.md | ❌ | catl.md | — |
| 帮我写一首关于酒的诗 | ✅ | 未召回 | ✅ | 未召回 | — |
| 帮我把这段话翻译成英文 | ✅ | 未召回 | ✅ | 未召回 | — |
| 感冒了吃什么药好 | ✅ | 未召回 | ✅ | 未召回 | — |
| 比亚迪刀片电池的技术参数 | ❌ | catl.md | ❌ | catl.md | — |
| 特斯拉的自动驾驶技术 | ✅ | 未召回 | ✅ | 未召回 | — |
| 苹果公司最新发布会讲了什么 | ✅ | 未召回 | ✅ | 未召回 | — |

## 排名变化的含义：rerank 在工作，但救不了也坏不了通过率

3 条"排名变"用例的共同模式：召回池里同时有茅台年报 PDF 段和 maotai.md 段，
hybrid 轮 Top1 是 PDF 段，rerank 轮 bge-reranker-v2-m3 把 maotai.md 段顶到第一。
两轮都判 ✅ 是因为两个源都含正确答案（黄金集判 Top5 命中期望源集合），
所以这类排序微调不改变通过率——但它证明 rerank 确实在重排候选，+0pp 不是"没生效"。

换言之：当前黄金集的正例用例里，正确答案段本就在 Top5 且多源冗余，
rerank 只能在"都对"之间调顺序，没有"错排可救"的空间。

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
