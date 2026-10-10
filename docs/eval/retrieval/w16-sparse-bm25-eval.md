# W16 #2 ADR-0017 候选 A（真 BM25）评估报告

> 状态：**结案。对照裁决：不切换**——BM25 正例 +3.4pp 过提升门槛，但负例拒答 7/10→3/10
> 破守线（matchtext 路的覆盖率裁决是负例防线，BM25 路召回无门槛致 4 案新增误召回）。
> 实现完整保留在 `ai.rag.sparse.impl=bm25` 旗标后，补召回门槛后可重评（第六节）。
> 取证口径：手工构造 BM25 稀疏权重排序验证挂账案可解性——不可解则裁决"无病灶不升级"
> 不动设施；可解则走 W15 #1 同款对照。脚本 [w16-bm25-probe.py](../scripts/w16-bm25-probe.py)
> （可复现：默认从 localhost:6333 scroll 现网快照重算）。

## 一、取证实录（三步，2026-10-10）

### ① 字面铁证：语料里就有"品牌壁垒"

`maotai.md` 第 17 行原文：「品牌壁垒深厚，提价能力强，渠道利润丰厚」——查询锚定词在
期望文档中字面存在。病灶不在语料。

### ② 现状 sparse 路失手根因：覆盖率硬裁决，不是候选网

改写后查询"贵州茅台和宁德时代哪家的品牌壁垒更厚"经 FullTextNormalizer 后是**单个
16 字 CJK token**，SparseCoverage 切出 17 个二元组。双实体查询决定了任何单公司文档
最多命中自家那一段：maotai.md 命中 {贵州, 州茅, 茅台, 品牌, 牌壁, 壁垒}≈6/17≈0.35，
catl.md 命中 {宁德, 德时, 时代}≈3/17≈0.18——**双双低于 0.5 放行阈值，全斩，未召回**。
0.5 阈值是 W8 adversarial 判例（"比亚迪刀片电池"对 catl.md 覆盖率 0.1 被拒）定下的
精度防线，不能为挂账单条静默改（计划纪律），于是该案结构性卡死。

### ③ 真 BM25 模拟：挂账案全部可解

Okapi BM25（k1=1.5，b=0.75）对现网 303 chunks（PDF 年报 301 + maotai/catl 各 1）全量
打分排序。分词用 CJK 二元组（与现状 sparse 同 token 集，**隔离唯一变量：BM25 排序信号
vs 覆盖率硬裁决**——对结论方向保守，词级分词只会更好）：

| 挂账案（改写后查询） | 期望 | BM25 排名 | Top1-Top2 分差 |
|---|---|---|---|
| 品牌壁垒：贵州茅台和宁德时代哪家的品牌壁垒更厚 | maotai.md | **Top2**（可解，Recall@5 口径通过） | Top1=catl.md（+5.11） |
| 品牌壁垒（原始查询）：这两家公司里谁的品牌壁垒更厚 | maotai.md | **Top1** | +2.67 |
| 碳酸锂：碳酸锂价格波动对宁德时代的影响 | catl.md | **Top1** | +38.86（碾压） |
| 赤水河：贵州茅台 赤水河流域环境容量约束 | maotai.md | **Top1** | +30.68（碾压；dense 路本轮 0.64 误中年报的失败案） |

**可解性结论：成立。** 四案全部进 Top5，三案 Top1 且分差碾压级。

诚实注记：改写后品牌壁垒案 BM25 也只把 maotai.md 排 Top2（catl.md 靠"宁德时代"二元组
群 + "哪家/公司"噪声险胜）——BM25 不是银弹，双实体查询的字面信号本身分裂；但
Recall@5 口径（Top-K 内即过）下足以结案。

## 二、候选实现调研（2026-10-10 核实）

| 路径 | 做法 | 账 |
|---|---|---|
| **A1 Qdrant 服务端 BM25**（推荐先行） | 容器升 ≥1.15.2（现 v1.15.1）→ collection 加 sparse 命名向量 + IDF modifier（服务端维护 IDF，v1.18 起可免重建给存量 collection 加向量）→ 摄入/查询走 `Document{text, model:"qdrant/bm25"}` 推理对象 | **自托管开源版可用**（官方 inference 文档：In-cluster BM25 三形态全 Available；非 Cloud 专属）；Java client 官方支持（`Points.Document` + `QueryFactory.nearest`）——ADR-0017 决策点③"Spring AI 零 sparse 支持"绕行方案 = 绕开 VectorStore 抽象直走 gRPC client；零新增进程依赖 [Qdrant BM25 docs](https://qdrant.tech/documentation/inference/inference-bm25/) |
| A2 FastEmbed 本地 sidecar | Python sidecar 算稀疏向量再 upsert | BM42 多语言不支持（官方对照表 Multi-lingual: No，中文排除）；fastembed 需 pip + stopwords 文件下载（本机 pip 已实测超时）；新增 Python 依赖面，违 A5 收敛方向 |
| A3 自建 Java BM25 | 应用侧维护 IDF 统计表 + 自算稀疏权重 upsert | 分词完全自控（可接中文词级分词），工程量最大，IDF 随摄入重建漂移——A1 中文分词翻车的兜底 |

**关键未知数：服务端 BM25 的中文分词行为。** FastEmbed 系 tokenizer 对 CJK 的切分
（整 run 单 token / 逐字 / 二元组）官方文档未言明——若整 run 单 token，中文场景直接
不可用，转 A3。该未知数可在改动任何生产设施前用一次性 probe 闭环：本地容器升 1.15.2+
→ 建测试 collection → upsert 三条中文 chunk → 查询"品牌壁垒"观察行为（半小时级）。

## 三、A1 probe 实录与判决（2026-10-10，探针容器 v1.18.2 即焚，未碰生产）

Owner 裁决走 A1 后，先闭环"中文分词行为"未知数。探针容器独立数据卷
（`qdrant-probe` @16333，用完即焚），脚本 [w16-bm25-server-probe.py](../scripts/w16-bm25-server-probe.py)
+ [w16-tokenizer-discriminate.py](../scripts/w16-tokenizer-discriminate.py)（可复现）。

**可用性验证通过**：`Document{text, model:"qdrant/bm25"}` 推理对象自托管 upsert 成功，
服务端 BM25 转换真实可用（340 字 maotai chunk → 46 稀疏项）。

**分词规则判别（决定性）**：

| 查询 | 结果 | 推论 |
|---|---|---|
| 「品牌壁垒深厚」（文中标点界内完整片段） | ✅ 命中 | 整段成单 token |
| 「品牌壁垒」（该片段的子串） | ❌ 零命中 | 无词级切分 |
| 「品」「品牌」「茅台」 | ❌ 零命中 | 非逐字、非词级 |
| 「12987」整串 / 「129」片段 | ✅ / ❌ | ASCII 同规则：整串单 token |

**判决：A1 服务端 tokenizer 把标点/空白界内 CJK 连续段整段当单 token**——中文无词内
分隔，检索退化为全等匹配，比现有 MatchText 二元组路还差。**中文场景不可用，A1 出局。**

**probe 红利：A3 账大幅瘦身。** 服务端 IDF modifier 与 tokenizer 无关——应用侧只需
分词 + 算 TF 权重（BM25 公式中不含 IDF 的部分）upsert 稀疏向量，**IDF 仍由 Qdrant
服务端在 collection 级自动维护**（upsert 即更新）。ADR-0017 当年给候选 A 记的最大成本
"自维护 IDF 统计表、随摄入重建漂移"整个消失，A3 从"工程量最大"降为：分词器
（复用 SparseCoverage 二元组逻辑，A11-1）+ TF 权重 + 摄入/查询挂稀疏向量。

## 四、路径再裁决（Owner 已确认，见开工口令记录）

A1 翻车 → 预案兜底 **A3-lite（已执行）**：自建中文分词 + TF 权重，sparse 向量 + 服务端
IDF modifier。对照设计同第五节预案不变；签名判变键扩 sparse 形态位（改造规模超一天
已在开工口令中获 Owner 批准）。

## 五、对照实验设计预案（裁决走 A 后启动，W15 #1 同款）

- 变量唯一：语料/分块/dense 路/rrf-k/recall-top-k/黄金集冻结，只动 sparse 路实现
  （MatchText+覆盖率 → BM25 稀疏向量）；env 可切换，签名判变键扩 sparse 形态位
- 守线：Recall@5 ≥93%（27/29）；负例零回归（7/10 起）；挂账 3 条命运逐案列出
- 裁决门槛沿用：Recall 提升 ≥2pp 才谈切换，打平保持现状（现状优先零成本）
- 成本一页账：摄入增量（sparse 向量生成与存储）+ 查询延迟增量（官方口径
  +0.6~1.5ms/query [Qdrant hybrid docs](https://qdrant.tech/documentation/search-tuning/hybrid-search/)）

## 六、A3-lite 对照实录与裁决（2026-10-10，真实 bge-m3 全量复跑 39 案）

实现落地（四步，全部 TDD）：`5eefa75` 编码器（分词单点收口 + TF 饱和/长度归一，
murmur3 锚定向量交叉验证）→ `447ac07` Qdrant v1.15.1→v1.18.2 全仓升版
（create_vector_name 前置）→ `23d38dc` 摄入链路（sparse 写入器 + avgdl 两遍法 +
tokenSum 入 manifest + 签名扩 sparse 位）→ `2e2459c` 查询召回（BM25 SparseRecall +
impl 互斥切换）→ `9108143` 端到端 IT（v1.18.2 容器直击子串病灶案 Top1）。
262 单测 + 16 IT 全绿。

### 对照结果（基线 = W16 #1 matchtext 复跑，变量唯一：sparse 路实现）

| 指标 | matchtext 基线 | bm25 臂 | 判决 |
|---|---|---|---|
| 正例 Recall@5 | 27/29（93.1%） | **28/29（96.6%）** | 守线 ✅，+3.4pp 过 ≥2pp 提升门槛 |
| 负例拒答率 | 7/10 | **3/10** | **守线破 ❌（-40pp，4 案新增误召回）** |
| M1 验收门 | 通过 | 通过（0.966 ≥ 0.7） | — |

挂账 3 案命运：品牌壁垒 ❌→✅（修复——改写后双实体查询 maotai.md 进 Top5，
Top1=catl.md 与离线模拟 Top2 位预测一致，交叉验证成立）；碳酸锂 ✅→✅ 维持；
赤水河 ❌→❌ 未解（dense topScore 0.64 依旧、Top1 仍年报 pdf——离线模拟曾判 Top1
可解，差异在模拟只排 sparse 分而真实是 RRF 融合：dense 把 301 chunk 的年报 pdf
顶满前排，sparse 单点信号被稀释。模拟对融合格局过于乐观，诚实记一笔）。

### 负例破线根因：防线在覆盖率裁决，不在 BM25

新增误召回 4 案（特斯拉自动驾驶→catl.md、写诗→年报 pdf、苹果发布会→年报 pdf、
OpenAI 模型→年报 pdf）**全部 dense 空收（topScore=--）**——召回完全来自 sparse 路。
matchtext 路的负例防线是 SparseCoverage 应用侧覆盖率硬裁决（子串覆盖率 <0.5 全斩，
W8 判例定下）；BM25 路 nearest 召回即有效，无绝对分门槛——「酒」「发布」「模型」
任一单词项命中即进 RRF 候选，dense 空收时 sparse 结果独占 Top5 名额。服务端 IDF
压低了高频词权重，但 RRF 只看排名不看分。残余误召回 3 案（比亚迪/买入建议/五粮液）
两臂一致；拒答 3 案（天气/感冒/翻译）两臂一致。

### 裁决（按第五节预登记规则）

**不切换，matchtext 维持现状。** 正例 +3.4pp 过提升门槛，但负例零回归守线被破，
净交换比为负（+1 正例 vs -4 负例）。`ai.rag.sparse.impl` 缺省保持 matchtext；
BM25 实现（编码器/摄入/查询/IT 全套）保留在 `impl=bm25` 旗标后不删——切换门面
已建，重评零重建成本。

**重评触发条件（登记）**：BM25 路补召回门槛——候选方向：sparse 路绝对分/归一化
分数闸门（等效覆盖率裁决的精度防线），或 sparse-only 命中（dense 空收）时的拒答
复核。补足后按同款对照重跑，裁决规则不变。

#3 rerank 重评条件项：BM25 未落地、召回格局未变 → **不触发**。

### 成本一页账

- 改造：5 commit（编码器/升版/摄入/召回/IT），核心变更每步 ≤150 行，全程 TDD
- 运行：摄入侧每 chunk 一次本地分词计数（零 API）；查询侧零新增 API 调用；
  存储 +1 稀疏命名向量/point；Qdrant 升 v1.18.2（既有容器重建一次）
- 回滚：env 切回 matchtext 即退（签名判变自动全量重建），零代码回滚
