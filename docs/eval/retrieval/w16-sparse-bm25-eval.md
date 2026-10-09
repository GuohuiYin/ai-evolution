# W16 #2 ADR-0017 候选 A（真 BM25）评估报告

> 状态：**取证完成，待 Owner 裁决实现路径**。
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

## 三、待 Owner 裁决

① 路径选择：**A1 先行 probe（推荐）** / A3 直接自建 / 放弃（维持候选 B 现状结案）。
② 诚实备选摆上桌：根因毕竟是自制 SparseCoverage 的硬阈值，"修 B"（软化阈值/双实体
   拆分）成本远低于上 A——但方向是继续堆自制启发式 vs 业界标准形态（服务端 IDF +
   BM25 权重），且 W15 写死的重启条件本就是评估 A。本报告推荐仍走 A 评估：
   对照实验产出的数据同时回答"A 比 B 好多少"，若 +0pp 则照旧裁决不启用（rerank 先例）。

## 四、对照实验设计预案（裁决走 A 后启动，W15 #1 同款）

- 变量唯一：语料/分块/dense 路/rrf-k/recall-top-k/黄金集冻结，只动 sparse 路实现
  （MatchText+覆盖率 → BM25 稀疏向量）；env 可切换，签名判变键扩 sparse 形态位
- 守线：Recall@5 ≥93%（27/29）；负例零回归（7/10 起）；挂账 3 条命运逐案列出
- 裁决门槛沿用：Recall 提升 ≥2pp 才谈切换，打平保持现状（现状优先零成本）
- 成本一页账：摄入增量（sparse 向量生成与存储）+ 查询延迟增量（官方口径
  +0.6~1.5ms/query [Qdrant hybrid docs](https://qdrant.tech/documentation/search-tuning/hybrid-search/)）
