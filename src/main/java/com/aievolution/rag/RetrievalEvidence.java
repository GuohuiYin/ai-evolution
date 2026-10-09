package com.aievolution.rag;

import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.lang.Nullable;

/**
 * 检索证据（W16 #1）：命中清单 + dense 段最高相似度——阈值判罚标尺随结果外暴露。
 *
 * <p>{@code denseTopScore} 是相似度阈值（{@code ai.rag.similarity-threshold}）的校准数据来源：阈值卡在 dense
 * 检索段，因此无论单段还是 hybrid/rerank 两阶段，此处一律报 dense 段分数。hybrid 融合分（RRF， 0.0x 量级，见 {@link RrfFuser} 口径说明）与
 * rerank 分均为异构口径、与阈值不可比，不作此用。
 *
 * <p>未召回时分数缺席（{@code null}）——缺席本身即拒答判罚依据，eval 报告分布段据此分组。
 */
public record RetrievalEvidence(List<Document> hits, @Nullable Double denseTopScore) {}
