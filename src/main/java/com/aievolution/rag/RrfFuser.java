package com.aievolution.rag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.document.Document;

/**
 * W13 #2a：RRF（Reciprocal Rank Fusion）融合器——dense/sparse 两路召回结果按排名融合。
 *
 * <p>只吃排名不吃分数：两路分数异构（dense 余弦相似度 vs sparse MatchText 命中无分数）， RRF 的 {@code 1/(k+rank)}
 * 形式因此是天然可比的融合口径（rank 从 1 起）。 两路都中的文档获得两份贡献而自然升位——这正是混合检索想要的"交集加权"。
 *
 * <p>常数 k 配置化（{@code ai.rag.hybrid.rrf-k}，A11 防拍值）；同分按文档 ID 字典序打破， 保证同输入输出可复现（判罚留痕可比对）。
 */
public class RrfFuser {

  private final int k;

  public RrfFuser(int k) {
    if (k <= 0) {
      throw new IllegalArgumentException("RRF 常数 k 必须为正数: " + k);
    }
    this.k = k;
  }

  /**
   * 融合两路已排序召回结果。
   *
   * @param dense 向量路命中（按相似度降序）
   * @param sparse 字面路命中（MatchText 无排序信号，顺序即排名——ADR-0017 已明示此近似）
   * @return 按 RRF 分降序的去重并集；{@link Document#getScore()} 承载融合分（当前最末端阶段分数）
   */
  public List<Document> fuse(List<Document> dense, List<Document> sparse) {
    Map<String, Double> scores = new HashMap<>();
    Map<String, Document> docs = new HashMap<>();
    accumulate(dense, scores, docs);
    accumulate(sparse, scores, docs);

    List<Map.Entry<String, Double>> ranked = new ArrayList<>(scores.entrySet());
    ranked.sort(
        Map.Entry.<String, Double>comparingByValue()
            .reversed()
            .thenComparing(Map.Entry.comparingByKey()));

    return ranked.stream()
        .map(e -> docs.get(e.getKey()).mutate().score(e.getValue()).build())
        .toList();
  }

  private void accumulate(
      List<Document> hits, Map<String, Double> scores, Map<String, Document> docs) {
    for (int i = 0; i < hits.size(); i++) {
      Document doc = hits.get(i);
      docs.putIfAbsent(doc.getId(), doc);
      scores.merge(doc.getId(), 1.0 / (k + i + 1), Double::sum);
    }
  }
}
