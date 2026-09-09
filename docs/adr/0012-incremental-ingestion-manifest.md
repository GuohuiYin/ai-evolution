# ADR-0012：知识库增量摄入——SHA-256 manifest 三类分派

## 状态

已接受（W8-2，2026-09-09）

## 背景

W8 之前每次启动对知识库全量重算 embedding：Qdrant upsert 幂等（不产生重复向量），
但 embedding API 调用与启动耗时全量重烧。语料增长后不可持续。

## 决策

引入 `KnowledgeManifest`（`build/knowledge-manifest.json`，不入 git）：
记录每文件 SHA-256 + chunkIds。启动时三类分派——未变跳过（零 embedding 调用）、
变更整删重写、磁盘消失文件清理向量。

## 替代方案与否决理由

| 方案 | 否决理由 |
|---|---|
| 维持全量重算 | embedding 调用费与启动耗时随语料线性增长 |
| 状态存 Qdrant | 读改写一致性复杂一档；本地开发删库即丢状态。build/ 文件"删了全量重建"天然自愈 |
| 按 mtime 判断变更 | 不可靠（touch/迁移动 mtime 不动内容）；内容哈希才是语义正确的判据 |

## 附带修复与发现

- 修复存量隐患：文件变短导致分块数减少时旧块向量残留（整删重写天然解决）
- 新增零分块告警：TokenTextSplitter 默认丢弃短于 minChunkSizeChars 的文本（实测发现），
  短文件不入库必须在日志可见

## 后果

- 二次启动零 embedding 调用（日志可观测："N 个文件全部未变更"）
- 测试夹具语义同步：IT 必须用每轮唯一的 manifest 路径（跨轮复用会跳过摄入，已在 W8-2 踩过）
