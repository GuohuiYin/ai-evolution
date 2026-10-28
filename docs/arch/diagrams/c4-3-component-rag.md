# C4 L3：组件图 · rag 检索域

> 本张聚焦 **rag 包**——摄入侧（写入）与检索侧（读出）两条链，以及每个数据裁决
> 过的开关在哪。截止 2026-10-28（W24 收官态）。

```mermaid
C4Component
  title 组件图：rag 检索域（com.aievolution.rag）

  Component_Ext(ragchat, "RagChatService", "chat 域", "检索调用方（含零召回重试防线）")
  Component_Ext(builder, "Builder（loop 域）", "loop 域", "ReAct 取证时的检索调用方")

  Container_Boundary(rag, "rag 域（主进程内）") {
    Component(rewriter, "QueryRewriter", "DeepSeekQueryRewriter / PassThrough", "指代消解+查询规范化；温度 0（确定性任务）；开关 ai.rewrite.enabled")
    Component(retriever, "KnowledgeRetriever", "VectorStoreKnowledgeRetriever", "dense 向量召回（阈值 0.5，召回 topK=20）")
    Component(sparse, "SparseRecall", "QdrantSparseRecall / QdrantBm25SparseRecall", "sparse 字面召回：matchtext 全文索引（缺省）/ bm25 应用侧分词（旗标切换，W25 #4 重评清仓）")
    Component(fuser, "RrfFuser", "RRF 融合", "dense+sparse 融合（k=60，敏感性实测锁值）；hybrid 总开关默认开（+21pp 裁决）")
    Component(reranker, "RerankerClient", "SiliconFlowRerankerClient", "精排段 bge-reranker-v2-m3——默认关（+0pp 裁决不启用）")
    Component(diversity, "RetrievalDiversityGuard", "主体配额重排", "≥2 在库主体时按主体保底名额重排截口，防 top-k 垄断（W23 双证据达标翻默认）")
    Component(ingestor, "KnowledgeBaseIngestor", "启动摄入", "PDF/Markdown 分块（800 token）→ embedding → 写入；幂等")
    Component(manifest, "KnowledgeManifest", "SHA-256 增量清单", "未变文件零 embedding 调用（ADR-0012）；损坏即全量重建自愈")
    Component(sparsewriter, "SparseVectorWriter", "QdrantSparseVectorWriter", "sparse 路写入侧：与 impl 旗标同签名，切换即全量重建")
    Component(tokenizer, "SparseTokenizer / FullTextNormalizer", "分词/归一化", "bm25 臂应用侧分词（k1=1.5 b=0.75 经典值）")
  }

  System_Ext(qdrant, "Qdrant", "dense+sparse 存储")
  System_Ext(siliconflow, "SiliconFlow", "embedding bge-m3 / rerank")
  System_Ext(deepseek, "DeepSeek API", "查询改写")

  Rel(ragchat, rewriter, "改写（含会话历史）")
  Rel(builder, rewriter, "子问题规范化")
  Rel(rewriter, deepseek, "改写调用", "HTTPS，温度 0")
  Rel(rewriter, retriever, "dense 路")
  Rel(rewriter, sparse, "sparse 路")
  Rel(retriever, qdrant, "向量查询", "gRPC")
  Rel(sparse, qdrant, "全文/稀疏查询", "gRPC")
  Rel(retriever, fuser, "召回集")
  Rel(sparse, fuser, "召回集")
  Rel(fuser, reranker, "（开关关时旁路）")
  Rel(reranker, siliconflow, "/rerank", "HTTPS")
  Rel(fuser, diversity, "截口重排")
  Rel(diversity, ragchat, "RetrievalEvidence（带回流标记）")

  Rel(ingestor, manifest, "变更判定（SHA-256）")
  Rel(ingestor, siliconflow, "embedding", "HTTPS")
  Rel(ingestor, qdrant, "dense 写入", "gRPC")
  Rel(ingestor, sparsewriter, "sparse 写入")
  Rel(sparsewriter, tokenizer, "bm25 臂分词")
  Rel(sparsewriter, qdrant, "sparse 写入", "gRPC")
```

## 读图要点

- **两阶段范式**（ADR-0017）：宽松召回（topK=20）→ 融合/精排 → 截断（top-k=5）；
  线上与 eval 共用同一份配置（application.yml `ai.rag.*`），保证「评估口径=线上口径」。
- **每个开关都有裁决报告**：hybrid +21pp 开、rerank +0pp 关、diversity 双证据达标开、
  zero-recall-retry A/B 探针开——翻旗标的姿势全部是黄金集数据（课程地图触发点清单）。
- **阈值悬崖病灶坐标**：dense 阈值 0.5 在 retriever 组件内——W24 涉案案 top 0.4817
  惜败于此，重试防线挂在 RagChatService（域外），治本留给 W25 #4。
- **摄入=写入签名联动**：sparse impl 切换并入向量签名判变键，换实现触发全量重建，
  不会半新半旧混存。
