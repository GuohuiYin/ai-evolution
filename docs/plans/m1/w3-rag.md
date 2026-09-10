# W3 回顾：RAG 检索增强全链路（已完成 ✅）

> 状态：2026-09-03 完成并验收（push 至 `6d78d81`）。本文档为补录回顾。
> 对应 PPT：W3「L2 Context（上）」+ W4「L2 Context（下）」的纯向量部分。

## 实际交付

| Step | 交付物 | 关键决策 | Commit |
|---|---|---|---|
| 1 | Qdrant 向量库 + Testcontainers 集成测试基座 | 手工构建 `QdrantVectorStore` 须显式 `afterPropertiesSet()` 才建表 | `8ca01e1` |
| 2 | 文档摄入管道（读取→分块→向量化→入库） | 幂等 ID = UUIDv3(文件名#块序号)；`ai.knowledge.ingest.enabled` 开关 | `4b1c3df` |
| 3a | 手动编排 RAG 问答 | **不用 Advisor 黑盒**：引用随响应返回（红线 03）；空检索硬拒答（防幻觉闸门）；免责声明服务端追加（红线 01） | `ded7f17` |
| 3b | 页面引用卡片 + eval 雏形 | golden-set 6 条（4 正 2 负）+ `RetrievalEvaluator`，真实 bge-m3 跑通 6/6 | `0a97312` |
| 4 | Qdrant 入驻 Minikube，集群全链路冒烟 | K8s DNS 服务发现；emptyDir 学习用（生产应 StatefulSet+PVC，注释留档） | `6d78d81` |

## 技术账

- Embedding：SiliconFlow `BAAI/bge-m3`（OpenAI 兼容协议，base-url 必须含 `/v1`）
- Chat：DeepSeek（`spring.ai.model.chat=deepseek` / `embedding=openai` 多供应商分工）
- 检索参数：topK=4，相似度阈值 `ai.rag.similarity-threshold`（默认 0.5，可调参）
- 测试：11 单测 + 4 IT 全绿

## 踩坑记录（进排障手册）

**同 tag 镜像覆盖陷阱**：`minikube image load` 默认不覆盖被运行容器引用的旧镜像，
rollout restart 后 Pod 仍跑旧代码，DeepSeek 凭预训练知识"假装"RAG 成功（假阳性）。
解法：缩容 → 删镜像 → 重新载入 → 恢复。
教训：**冒烟必须验证响应结构（sources 字段存在性），不能只看内容像不像**。

## 相对 PPT 的欠账（已编入后续周）

- ❌ ETL 仅 Markdown，PDF/Word/HTML 公告解析未做 → W5-Step4
- ❌ 统一元数据规范（数据源+时点结构化） → W5-Step4
- ❌ M1 验收门未正式过：黄金集仅 6 条、topK=4，未达「20 条 Recall@5 ≥ 0.7」 → W5-Step3
