# M1：地基 + RAG（W1-W5）

> 对应 PPT Slide 17。本页是阶段索引：验收门与周文档导航。
> 阶段主线：从空仓库到"带 RAG + 工具调用 + eval 验收门"的可运行系统。

## 周索引

| 周 | 主题 | 文档 | 状态 |
|---|---|---|---|
| W1 | 工程地基与云原生基座 | [w1-foundation.md](w1-foundation.md) | ✅ 已完成（含偏离记录） |
| W2 | 真实模型接入与工程化配套 | [w2-real-model.md](w2-real-model.md) | ✅ 已完成（L1 欠账→W4） |
| W3 | RAG 检索增强全链路 | [w3-rag.md](w3-rag.md) | ✅ 已完成（M1 验收门→W5） |
| W4 | L1 Prompt 工程补课 | [w4-l1-prompt-engineering.md](w4-l1-prompt-engineering.md) | ✅ 已完成 |
| W5 | Function Calling + M1 验收门 | [w5-function-calling.md](w5-function-calling.md) | ✅ 已完成 |
| — | **验收记录** | [m1-acceptance.md](m1-acceptance.md) | ✅ Recall@5 86.7% |

## M1 验收门

- [x] 正例 Recall@5 ≥ 0.7（首跑 85.7%，PDF 入库语义升级后 86.7%）

## 阶段沉淀（被后续阶段反复引用的）

- **黄金集语义评审铁律**：语料扩容必须伴随黄金集评审（PDF 入库跌门逼出 expectSources 多源语义）
- mock 先行策略（ADR-0004）：数据源先 mock 跑通链路，再换真源
- eval 验收门机制（ADR-0007）：不过不进下一阶段
- 手动编排 RAG（ADR-0006）：引用来源可返回、空检索硬拒答
