# Embedding 模型选型报告 v1（调研论证型）

> 时点：2026-09-10（W9）· 性质：调研论证，非实测
> 结论依据：公开 benchmark（MTEB/C-MTEB）+ 本项目硬约束矩阵
> 实测对照实验已列入进阶阶段（W14+），届时用本项目检索黄金集 39 条做 Recall@5 对比

## 1. 需求矩阵（先定义"我们要什么"）

| # | 需求 | 来源 | 权重 |
|---|---|---|---|
| R1 | 中文金融语料检索质量 | 业务域：年报/公告/研报全中文 | ★★★ |
| R2 | OpenAI 兼容 API（云托管，不本地部署） | 学习项目无 GPU；Spring AI OpenAI 兼容客户端现成 | ★★★ |
| R3 | 长上下文（年报分块 800 token，留有余量） | `ai.rag.chunk-size: 800` | ★★ |
| R4 | 低成本 / 有免费额度 | 个人学习项目预算约束 | ★★ |
| R5 | 支持混合检索扩展（dense + sparse） | 混合检索是 M3 候选（已积累 5 条证据） | ★ |
| R6 | 维度可控（存储/性能权衡） | Qdrant 存储成本随维度线性增长 | ★ |

## 2. 候选对照（公开 benchmark 数据）

| 模型 | 维度 | 上下文 | C-MTEB / MTEB 表现 | 许可 | 本项目适配性 |
|---|---|---|---|---|---|
| **BGE-M3**（BAAI） | 1024 | 8192 | C-MTEB 长期开源基线；MTEB 综合约 63.0 | MIT | ✅ 全部满足；**唯一同时支持 dense+sparse+multi-vector 三模式**，直接支撑 R5 |
| **Qwen3-Embedding-8B**（阿里，2025-06） | 4096（MRL 可裁剪至 512/1024/2048） | 32K | MTEB Multilingual 70.58（发布时榜首）；C-MTEB 73.84，中文当前最强之一 | Apache 2.0 | ✅ R1 最强；维度裁剪满足 R6；但 8B 推理成本更高 |
| Qwen3-Embedding-0.6B | 1024 | 32K | 多基准已超 BGE-M3 | Apache 2.0 | ✅ 低成本备选 |
| bge-large-zh-v1.5（BAAI） | 1024 | 512 | 纯中文短文档略优于 M3 | MIT | ❌ R3 不满足：512 token 上下文放不下 800 token 分块 |
| text-embedding-3-large（OpenAI） | 3072 | 8192 | MTEB 64.6；中文一般 | 商业 | ❌ R1 弱、R4 弱（$0.13/M tokens） |
| Cohere Embed v4 | 1536 | 128K | MTEB v2 65.2；多模态 | 商业 | △ 企业场景强，但学习项目成本/必要性不匹配 |

**benchmark 使用纪律**（写进报告是因为这是选型最常见的坑）：
MTEB 有 v1 / v2 / MMTEB / C-MTEB 多个版本与榜单，不同模型挂的榜单不同，
**跨榜横比会误导**；公开榜单回答"普遍能力"，不回答"对我们语料的能力"——
后者只有自己的黄金集能回答，这正是进阶阶段实测的意义。

## 3. 结论

**维持 BGE-M3（经 SiliconFlow 托管）为当前选型**，理由按权重排序：

1. R1：C-MTEB 开源基线，中文金融语料表现经社区广泛验证，且本项目 W8 检索基线
   （Recall@5=72%，39 条黄金集）就是用它跑的——**基线与模型绑定，换模型即废基线**
2. R5：三模式（dense/sparse/multi-vector）是混合检索路线（M3 候选）的现成地基，
   换其他模型等于砍掉这条演进路线
3. R2/R4：SiliconFlow 托管 + OpenAI 兼容，配置切换即可，零运维

**明确记录在案的次优选项**：Qwen3-Embedding-8B。若进阶实测显示其在本项目黄金集上
Recall@5 显著优于 BGE-M3（阈值建议：≥5pp），则触发换型评估——届时需重建基线并走
ADR 流程。

## 4. 进阶实测计划（W14+ 候选，退出本报告的"理论到实践"钩子）

1. 候选：bge-m3 vs Qwen3-Embedding-8B vs bge-large-zh-v1.5（均为 SiliconFlow 现成模型，零开通成本）
2. 方法：同一黄金集 39 条，各跑一轮 Recall@5；控制变量（chunk-size / topK / 阈值不变）
3. 成本预估：39 条 × 3 模型，embedding 调用量极小，< ¥1
4. 产出：`embedding-selection-v2.md`（实测版），结论进 ADR

## 参考来源

- Qwen3 Embedding 技术报告（MTEB Multilingual 70.58 / C-MTEB 73.84）：[arXiv:2506.05176](https://arxiv.org/pdf/2506.05176v2)
- MTEB 榜单版本差异与 2025-2026 排名：[MTEB Leaderboard 汇总（ailog）](https://app.ailog.fr/en/blog/news/huggingface-rag-models)
- 中文检索（C-MTEB）候选对比：[learn-ai-engineering embedding 选型参考](https://github.com/bob798/learn-ai-engineering/blob/main/content/03-rag/mock-interview/06_embedding%E9%80%89%E5%9E%8B%E5%8F%82%E8%80%83%E4%B8%8E%E5%90%88%E6%88%90Query.md)
- 中文场景选型综述（BGE-M3/Jina/Qwen3 对比表）：[稀土掘金 RAG 优化长文](https://juejin.cn/post/7621122531537715226)
