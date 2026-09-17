# W13 chunk-size 对照实验（400/800/1200 三轮全量回归）

> 2026-09-17 本地跑批，`AI_EVAL_ENABLED=true` + `AI_RAG_CHUNK_SIZE=<N>`，真实 bge-m3，
> Recall@5 / 相似度阈值 0.5 / topK=5（与线上口径一致），黄金集 v3（39 条）三轮不变。
> 每轮跑前删 Qdrant `knowledge_base` 集合 + 删 `build/knowledge-manifest.json`——
> 增量摄入按文件 SHA-256 判变，换 chunk-size 不触发重摄入，不删则评的是上一档的旧块（实测坑，见末段）。

## 总览（三轮结果完全一致）

| chunk-size | 分块数（points） | 正例 Recall@5 | 负例拒答率 | M1 门（≥0.7） |
|---|---|---|---|---|
| 400 | 523 | 21/29（72%） | 7/10 | ✅ 通过 |
| 800（现值） | 303 | 21/29（72%） | 7/10 | ✅ 通过 |
| 1200 | 211 | 21/29（72%） | 7/10 | ✅ 通过 |

分类别三轮也完全一致：normal 14/15、paraphrase 2/4、literal 2/4、boundary 3/6、adversarial 7/10。

失败集合三轮相同（11 条）：12987 三条（normal×1 + paraphrase×2）、literal 两条
（赤水河/SNE 37%）、boundary 指代三条、adversarial 误召回三条（五粮液/比亚迪/值得买入）。
唯一差异是失败**方式**：boundary「谁的风险和原材料价格关系最大」在 400/1200 档误命中
茅台年报 PDF、800 档未召回——均为失败，不影响计数。

## 为什么三轮无差异：粒度不是当前语料的瓶颈变量

- 两个小 md（catl.md 831B / maotai.md 851B，约 280 中文字）在 400/800/1200 三档下
  **都是单块**（TokenTextSplitter 按 token 计，远低于最小档 400）——chunk-size 实际只影响
  年报 PDF 的切分（521/302/210 块）。
- 而失败用例几乎全部期望命中 .md（12987 三条、literal 两条、boundary 三条），
  这些文件自始至终单块，粒度变化对它们零影响。
- 失败模式仍是 W8 基线判读的语义盲区：数字代号（12987）、短原文片段（literal）、
  指代消解（boundary）——全是"向量相似度区分度不够"，不是"块切得不对"。
  **与 W8 结论互证：这类失分要靠混合检索（W13 #2）而非调分块。**

## 裁决：维持 chunk-size=800（现值即最优）

1. 三轮 Recall 无差异，改值零收益——不动默认值就是最诚实的结论
2. 400 档分块数 +72%（523 vs 303），embedding 成本与向量存储同步上涨，无召回回报
3. 1200 档分块更整但同样无召回收益，且更长块稀释检索得分密度，无采纳理由
4. 黄金集 v3 不变，本报告登记进 golden-set-changelog 作为 v3 下的对照实验档案

## 成本账（本轮实验）

三轮全量重建共 1037 块 embedding（523+303+211，SiliconFlow bge-m3 批处理）+ 三轮 × 39 条
查询 embedding，单轮端到端约 2.5 分钟。另跑一轮 400 档仅摄入补记分块数。

## 复跑方式（含实测坑）

```bash
# 每轮换档必须清库 + 清清单，否则增量机制按文件 SHA 判"未变更"整块跳过
curl -X DELETE http://localhost:6333/collections/knowledge_base
rm -f build/knowledge-manifest.json
AI_EVAL_ENABLED=true AI_RAG_CHUNK_SIZE=400 ./mvnw spring-boot:run   # 需 SILICONFLOW_API_KEY
```

> 挂账（非阻塞）：`KnowledgeBaseIngestor` 的增量判据只看文件 SHA，不看分块参数——
> chunk-size 变更后正常启动会拿旧块服务。本轮靠手工清库规避；若未来 chunk-size
> 进入频繁调参，应将分块参数哈希并入 manifest 判变键。
