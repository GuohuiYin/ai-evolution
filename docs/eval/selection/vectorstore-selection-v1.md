# 向量数据库选型报告 v1（调研论证型）

> 时点：2026-09-10（W9）· 性质：调研论证，非实测
> 结论依据：2025-2026 公开 benchmark + 本项目硬约束矩阵
> 核心判断：**在本项目量级下，选型由运维成本与生态决定，不由性能 benchmark 决定**——
> 主流向量库在 10 万级向量下性能全部过剩

## 1. 需求矩阵

| # | 需求 | 本项目实际值 | 权重 |
|---|---|---|---|
| R1 | 数据规模 | 研报/年报分块，当前千级，天花板 10 万级 | 决定一切 |
| R2 | 本地 Kubernetes（Minikube）单节点部署 | 云原生第一天原则 | ★★★ |
| R3 | 元数据过滤（payload filter） | 元数据三件套 source/docType/asOf 过滤检索是核心能力 | ★★★ |
| R4 | Java / Spring AI 生态集成 | Spring AI VectorStore 抽象 | ★★ |
| R5 | 混合检索扩展（dense + sparse） | M3 候选（已有 5 条证据） | ★★ |
| R6 | 运维成本（单人项目） | 无 SRE 团队 | ★★★ |
| R7 | 数据不出本机（金融语料敏感） | 私有化部署硬性要求 | ★★★ |

## 2. 候选对照

| | Qdrant | Milvus | pgvector | Chroma |
|---|---|---|---|---|
| 定位 | Rust 高性能专用库 | 企业级大规模专用库 | PostgreSQL 扩展 | 原型/教学库 |
| 适配规模 | 10 万~5 亿 | 亿级以上（GPU 加速） | <2000 万（建议 <5000 万换库） | 原型期 |
| Minikube 单节点 | ✅ 单容器即可 | ❌ 组件多（etcd/MinIO/多服务），单节点部署重 | ✅ 但需额外引入 PostgreSQL | ✅ 但非生产定位 |
| 元数据过滤 | ✅ 业界口碑最强（disk-mapped payload，过滤不掉性能） | ✅ | ✅（走 SQL） | 弱 |
| 混合检索 | ✅ 原生 sparse 向量支持 | ✅ | 需多阶段拼装 | ❌ |
| Spring AI 集成 | ✅ 官方 VectorStore 实现 | ✅ | ✅ | ✅ |
| 安全/RBAC | ✅ v1.10+ | ✅ | ✅（PG 原生） | ❌ 无安全机制 |
| 运维成本 | 低（单二进制） | 高 | 最低（若已有 PG） | 低 |

**公开 benchmark 参考**（100M 向量 / 768 维 / HNSW，2025 年实测）：

| 库 | 写入吞吐 | p99 查询延迟 | 过滤后 Recall@10 |
|---|---|---|---|
| Qdrant 1.10 | 42,000 vec/s | 18.4ms | 0.97 |
| Milvus 2.4 | 38,500 vec/s | 24.7ms | 0.94 |
| pgvector 0.7 | 12,400 vec/s | 58.3ms | 0.91 |

注意：这个量级的差距在 R1（10 万级天花板）下**全部无感**——千级向量任何库的查询都在
毫秒级。引用 benchmark 是为了说明"为什么性能不是我们的决策变量"。

## 3. 结论

**维持 Qdrant v1.15.1**，理由按权重排序：

1. R2/R6：Minikube 单节点下一个容器跑起来，单人运维可承受——Milvus 在这一条上直接出局
2. R3：payload 过滤是业界公认最强（元数据 disk-mapped，过滤场景吞吐不降），
   正好命中本项目元数据三件套过滤的核心能力
3. R5：原生 sparse 向量支持，与 BGE-M3 的 sparse 模式组成混合检索的现成地基
4. R7/R4：私有化部署 + Spring AI 官方集成（我们已在用 `QdrantVectorStore`）
5. pgvector 的唯一胜场是"已有 PostgreSQL"——本项目没有 PG，为它引入 PG 反而增加组件

## 4. 重评触发条件（选型报告必须写"什么时候该重新选"）

任一条件成立即触发重新评估并走 ADR：

1. 向量规模突破 100 万级（当前假设的 10 倍）
2. 需要多节点分布式 / 高可用（Minikube 单节点假设被打破）
3. 引入关系型存储且团队决定统一技术栈（pgvector 路线复活）
4. Spring AI 对某候选的集成质量显著恶化或停止维护

## 参考来源

- 100M 向量级 benchmark 与选型建议：[Vector Database Performance Benchmarks 2025](https://inductivee.com/blog/vector-database-performance-benchmarks-2025)
- 1M/10M 向量 QPS 与延迟对比 + 场景速查表：[Vector Database Complete Guide 2025](https://www.youngju.dev/blog/culture/2026-04-13-vector-database-embedding-similarity-search-guide-2025.en)
- 2025 竞争格局（HNSW vs DiskANN、TCO 分析、数据库矩阵）：[ai-system-design-guide 向量库对比](https://github.com/ombharatiya/ai-system-design-guide/blob/main/06-retrieval-systems/04-vector-databases-comparison.md)
- 安全合规维度对比（RBAC/加密/私有化）：[博客园 RAG 向量库深度对比](https://www.cnblogs.com/ljbguanli/p/19432729)
