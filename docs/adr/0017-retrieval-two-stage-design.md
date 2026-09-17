# ADR-0017：检索管线两阶段化设计——宽松召回 + 精排，数据裁决去留

- 状态：**待评审**（W13 #0 闸门：评审通过才动代码）
- 日期：2026-09-17（W13 #0 设计稿）

## 背景

当前检索单阶段：`topK=5 + similarity-threshold=0.5` 一刀切（Recall@5=72%，W8 基线）。
失分分析（golden-set 评审记录）指向两类病灶：**"没捞回来"**（字面匹配类，dense 向量对
专有名词/编号/年份的字面差异不敏感，5 条证据）与**"捞回来排错位/压线误杀"**（boundary 类，
卡在阈值边缘）。生产级范式是两阶段：宽松召回（topK 放大，可混合检索）→ reranker 精排
取前 N。两者均走黄金集数据裁决，不达标不启用（同 few-shot 裁决机制，ADR-0007）。

## 现状核实（设计前提，jar 层实证 2026-09-17）

1. **Spring AI 2.0.1 `QdrantVectorStore` 零 sparse 支持**（jar 内无任何 sparse/hybrid 类）——
   混合检索必须绕过 Spring AI 抽象，直调 `io.qdrant:client:1.18.0`（已随工程引入，
   含 SparseVectorConfig / QueryPoints / Prefetch / RRF Fusion 全套原生 API）
2. **Qdrant 服务端 v1.15.1** 支持 sparse 向量与原生 RRF 融合（query API prefetch+fusion）
3. **SiliconFlow bge-m3 的 OpenAI 兼容端点不暴露 sparse 输出**——sparse 向量需自行计算
   （BM25 类）或换路
4. reranker 走 SiliconFlow `/rerank` 端点（bge-reranker-v2-m3），非 OpenAI 兼容协议，
   需薄 HTTP 客户端（RestClient 直调，复用 SILICONFLOW_API_KEY，零新增密钥面 A5）
5. eval 门现成：`GoldenRetrievalEvalIT`（CI 替身）+ 本地 eval Runner（真实 bge-m3），
   "评估口径=线上口径"由共用 `ai.rag.*` 配置键保证——本设计延续该不变式

## 决策（推荐方案，随稿评审）

### 1. 配置结构（`ai.rag.*` 扩展，旧键保留为单阶段回退路径）

```yaml
ai:
  rag:
    # —— 既有单阶段键保留：两阶段全关时行为与今天完全一致（兼容回退）——
    top-k: 5                    # 语义变为"最终返回条数"（= rerank-top-n 的单阶段别名）
    similarity-threshold: 0.5   # 召回侧初筛阈值（dense 分数语义不变）
    chunk-size: 800             # W13 #1 对照实验后定稿
    # —— 新增两阶段键 ——
    recall-top-k: 20            # 召回放大倍数（4x），直接决定 rerank 计费量
    rerank:
      enabled: false            # 数据裁决达标才翻 true（默认关，裁决记录见 W13 #3）
      model: BAAI/bge-reranker-v2-m3
      top-n: 5                  # 精排后保留数，维持线上返回口径
    hybrid:
      enabled: false            # 同上，黄金集 Recall@5 ≥+5pp 才默认启用（W13 #2）
      rrf-k: 60                 # RRF 融合常数（经典默认），配置化防拍值
```

- `top-k` 不废弃：两阶段关闭时它仍是唯一口径；开启后由 `rerank.top-n` 接管最终条数，
  `top-k` 仅在 hybrid/rerank 全关时生效（配置互斥关系进 javadoc + 配置测试锁定）
- `recall-top-k: 20` 的理由：业界生产口径过取 50–200 进 reranker，但那是百万级语料的
  配比；本工程语料为个位数文档、全库 chunks 量级小，20 的绝对覆盖率已不低——
  **小语料取小值是规模适配，不是对标业界值**。仍按 #2 落地时 20/40 两档实测对比定稿

### 2. `KnowledgeRetriever` 返回语义（决策点 ② 的裁决）

- **返回类型不变**（`List<Document>`），契约不变式：调用方（RAG 通路/工具）零改动（开闭原则）
- **分数语义按阶段分层**：`Document.score` 承载"当前最末端阶段的分数"——单阶段=余弦相似度，
  开 rerank=reranker 分数；检索日志（stage=RETRIEVE）增打 recallHits/rerankTop 两段，
  判罚留痕不断档（A6）
- **阈值分层**：`similarity-threshold` 只管召回初筛（dense 分数），rerank 不设分数阈值
  （reranker 分数分布与余弦不同量纲，设阈值需另起数据校准——若裁决数据显示需要，
  以 `rerank.min-score` 增补，不在本期默认范围内）
- 空召回即拒答的现有语义不变（越界硬拒答是红线）

### 3. sparse 路选型（决策点 ③ 的裁决，本设计最大技术分歧点）

**先立业界标尺**：生产口径的 sparse 指**真稀疏向量**（BM25/SPLADE/BM42，统计或学习型），
Qdrant 原生混合检索也是这条路（sparse vector + prefetch + RRF fusion）；金融文本+表格
基准上 BM25 单独即可跑赢 dense（T2-RAGBench，2.3 万查询，p<0.001），含编号/年份/条款的
语料稀疏路可提升 20–40% 召回——我们的年报语料正中该场景，稀疏路的价值有实证背书。

三个候选，实测核实后的账：

| 候选 | 做法 | 账 |
|---|---|---|
| A. Qdrant 原生 sparse 向量（**业界标准形态**） | 自建 BM25/IDF 向量化（分词+词频表），sparse 与 dense 同 collection，Query API prefetch+RRF 原生融合 | 对齐业界标尺，但需自维护 BM25 统计表（摄入时更新 IDF），工程量最大；IDF 漂移需随摄入重建 |
| B. **全文索引关键词路 + 应用侧 RRF（推荐）** | Qdrant payload 全文索引（MatchText）捞字面命中，dense 路照常，两路按排名 RRF 融合（无分数用排名，RRF 本就只吃排名） | **轻量近似，非业界标准形态**——正当性来自语料规模（个位数文档），不动数据结构/摄入链路，工程量最小；字面匹配类病灶正对靶心 |
| C. 引入外部 sparse 模型服务 | 如 SiliconFlow 另寻 sparse embedding 端点 | 新增依赖面，违背 A5 收敛方向 |

**推荐 B，但必须说透**：B 是 BM25 的轻量代理而非等价物——MatchText 只有命中/不命中，
没有词频-逆文档频率的排序信号，字面路内部排名弱于真 BM25。选它的唯一理由是当前语料
规模下 A 的 IDF 自维护成本与收益不匹配；**若 B 的黄金集裁决数据不达标，直接升级 A**
（原生 API 已核实具备），不留死路。语料规模上量（几十篇以上）时无论数据如何都应重评 A。

### 4. RRF 融合（决策点 ④ 的裁决）

常数 k=60（RRF 论文经典值）**配置化**（`hybrid.rrf-k`）——防拍值约定（A11）：
默认值可以经典，但必须可被实验覆盖；#2 落地时以 k=30/60/100 三档跑黄金集敏感性，
差异 <1pp 则锁 60 不再议。

### 5. reranker 接入（决策点 ⑤ 的裁决）

- 锁定 bge-reranker-v2-m3 经 SiliconFlow `/rerank` 端点，薄封装 `RerankerClient`
  （RestClient 直调，协议非 OpenAI 兼容，不进 Spring AI 模型抽象）
- **成本账口径**（#3 验收要求"一页账"）：延用 `docs/eval/selection/` 一页账格式
  （官方单价 × 实测用量），rerank 按次/按 token 的官方计价以调用返回的 usage 字段实测，
  对照场景=W13 黄金集全量回归一次的总调用量，入 `reranker-selection-v1.md`

## 与既有机制的关系

- **评估口径=线上口径**：新配置键同时服务线上与 eval 门，不变式延续；
  `GoldenRetrievalEvalIT` 按配置切换单/双阶段跑对照（#2/#3 验收命令已预留在 M3 索引）
- **降级链正交**：rerank 调用失败属供应商故障，语义上不进 FailoverChatModel
  （那是 chat 模型链路）；rerank 故障应**降级回单阶段召回结果**而非报错——
  该降级策略进 #3 实现清单（本稿登记，防实现期遗忘）

## 明确不做（本期）

- 不做 GraphRAG/知识图谱（⛔ 范围外，能力地图在案）
- 不做检索结果缓存（W14+）
- 不做 reranker 自托管（vLLM 等）——学习项目聚焦管线范式而非基建
- sparse 候选 A（原生 BM25 向量）仅登记为 B 的升级路径，本期不实现

## 业界依据（2026-09-17 查证，非回忆）

- 两阶段 retrieve-then-rerank 是 2026 生产标准范式：Dense+Sparse → RRF → 过取 50–200 →
  cross-encoder 精排 → top 5–10；混合+精排比单阶段减少约 67% top-20 检索失败（Anthropic 公开研究）
- T2-RAGBench（arXiv 2604.01733，金融文本+表格，23,088 查询）：BM25 单独跑赢 dense，
  混合+神经精排最优（Recall@5=0.816）；HyDE/多查询对数值类查询收益有限
  （佐证 W11 HyDE 不采纳裁决）
- RRF k=60 等权起步是默认口径，仅黄金集显示排名敏感性才调参
- bge-reranker-v2-m3 为当前最佳开源多语 reranker（278M），精排典型收益 NDCG@10 +5~15pp

## 评审关注点（请 Owner 重点过）

1. 配置互斥关系（top-k vs rerank.top-n 的生效规则）是否认可
2. sparse 路选 B（全文索引+RRF）而非 A（原生 BM25）是否认可
3. `recall-top-k: 20` 初始值与"20/40 两档实测定稿"的收口方式
4. rerank 故障降级回单阶段（而非报错）是否符合韧性预期
5. 成本账口径（一页账格式 + 黄金集全量回归为对照场景）

## 后果

- 正：两阶段范式本体落地，召回/精排分层可独立裁决；配置化默认关保证不达标零风险
- 负：召回放大 + rerank 增加每次查询延迟（预估 +0.5-2s，#3 实测入账）与费用；
  hybrid 路引入 Qdrant 全文索引配置（摄入链路需建索引，工程量小但多一处状态）
- 后续触发条件：B 路数据不达标 → 升级 sparse 候选 A；语料规模上量 → 重评 IDF 自维护
