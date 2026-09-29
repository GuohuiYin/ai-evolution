# GraphRAG 认知笔记（W15 #3，蓝图 2.8"会用"档）

> 蓝图口径："只学概念与适用边界，不动手"。本笔记独立回答三问：
> ① 我们的语料特征是什么 ② GraphRAG 适用边界在哪 ③ 本项目结论是什么。

## 一、概念（2026-09-29 调研）

**GraphRAG**（Microsoft，2024-04，arXiv:2404.16130）：索引期对全语料逐块做 LLM
实体/关系抽取 → 构图 → Leiden 社区检测 → 每个社区生成摘要报告；查询分两种模式——
- **Local search**：从查询抽实体 → 图邻居扩展 → 邻接 chunk 喂 LLM（回答具体实体细节）
- **Global search**：查询映射到相关社区 → 各社区摘要分批生成部分答案 → 层层汇总
  （回答"这批文档在讲什么"类全局问题，这是 naive RAG 原理性无法回答的）

**LightRAG**（港大，2024-10）：去社区化的轻量路线——不做社区检测/社区摘要，
查询时一次 LLM 调用抽高/低层关键词 → 向量召回实体/关系 → 1-2 跳图遍历。
索引成本降至 GraphRAG 的约 1%，原生增量更新，但全局聚合能力弱于社区摘要路线。
[LightRAG 与 GraphRAG 对比](https://juejin.cn/post/7671500221185884223 "citation")

## 二、适用边界：什么语料/查询特征值得上

| 维度 | naive/hybrid RAG（本项目现状） | GraphRAG 值得上 |
|---|---|---|
| 查询类型 | 单跳、文档中心（"X 是什么/多少"） | 全局主题性（"这批文档讲什么"）、多跳关系链（"谁通过什么影响谁"） |
| 语料结构 | 文档相互独立、实体少 | 实体密集互联、关系跨文档散落 |
| 规模 | 小-中 | 万级以上 chunk，全局问题反复出现 |
| 更新频率 | 频繁（重建便宜） | 低（社区摘要牵一发动全身，增量困难） |
| 延迟预算 | 秒级 | 全局查询分钟级可接受 |

**成本结构（业界的账）**：索引期重投入——1M tokens 语料约 $20-100 + 数十分钟；
全局查询单次数百次 API 调用、约 61 万 tokens 输入、分钟级响应；语料大变更后社区
结构需重建（一份法律数据集测算：吸收新数据需重建 1399 份社区报告 ≈1400 万 tokens）。
[GraphRAG 机制与成本分析](https://juejin.cn/post/7671500221185884223 "citation")，
[LightRAG 成本测算论文报道](https://h5.ifeng.com/c/vivoArticle/v0020lNQ8opqWcveQ48bizGPIlENFG8YiYy8aQOWWkAaag8__?isNews=1&showComments=0 "citation")

**决策经验法则**：查询日志说话——当"连接型/影响型/全局型"问题占到可观比例且现有
RAG 答不好时，才在对应路径升级；否则是"为图而图"。2025 年基准还显示 GraphRAG 的收益
是数据集依赖的：多跳数据集（MuSiQue）上优于 naive RAG，长文档理解（QuALITY）上
反而更差。 [RAG vs GraphRAG 实践指南](https://medium.com/@Quaxel/rag-vs-graphrag-in-2025-a-builders-field-guide-82bb33efed81 "citation")，
[成本-性能权衡分析](https://openreview.net/pdf/f83fd96e8e5a8cbda99bfacad78d8dfdb902f6bf.pdf "citation")

## 三、本项目语料特征（实盘点）

- **3 个文件**：maotai.md（0.8KB 笔记）、catl.md（0.8KB 笔记）、茅台 2024 年报 PDF（3.6MB）
- **2 个实体**：贵州茅台、宁德时代——"图"只有一条边（可比公司关系）
- **查询分布**：黄金集 39 条全部单跳文档中心；boundary 类"双实体对比"看似多跳，
  实则两篇笔记并排即可回答，hybrid + 改写已达 Recall@5 93%
- **全局性问题：0 条**；语料周级可更新（W15 #1 已建成签名判变自动重建设施）

## 四、本项目结论

**不上 GraphRAG，理由三条（每条独立成立）：**
1. **无病灶**：全局性查询为零、多跳查询为零，GraphRAG 的杀手锏（global search）
   没有用武之地；现有残余失败（挂账 3 条）已定位为排序层边界体质，非图结构可解
2. **成本倒挂**：对 3 文件语料做全库 LLM 抽取+社区摘要，索引成本比整个语料的
   embedding 摄入（17 秒）高几个数量级，收益为零
3. **运维错配**：GraphRAG 增量更新是业界公认难点，与本项目"语料随时换、自动重建"
   的设施方向背道而驰

**重启条件（出现任一即重评，届时先看 LightRAG 而非全量 GraphRAG）：**
- 语料扩到数十家公司研报/公告，实体数上百且关系跨文档散落
- 查询日志中"谁通过什么影响谁"类多跳问题稳定出现且 hybrid 答不好
- 出现"总结这批材料的共同主题/风险"类全局性需求

## 参考

- [GraphRAG: New tool for complex data discovery（Microsoft Research）](https://www.microsoft.com/en-us/research/blog/graphrag-new-tool-for-complex-data-discovery-now-on-github/ "citation")
- [GraphRAG 机制、成本与边界（稀土掘金）](https://juejin.cn/post/7671500221185884223 "citation")
- [LightRAG vs GraphRAG 工程路线对比](https://www.agentlist.top/zh/articles/graphrag-vs-lightrag-comparison/ "citation")
- [RAG vs GraphRAG: A Builder's Field Guide（2025）](https://medium.com/@Quaxel/rag-vs-graphrag-in-2025-a-builders-field-guide-82bb33efed81 "citation")
- [Knowledge Graph vs RAG 决策矩阵（Atlan，2026）](https://atlan.com/know/knowledge-graphs-vs-rag-for-ai/ "citation")
