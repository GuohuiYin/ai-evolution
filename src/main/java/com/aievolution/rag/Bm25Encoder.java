package com.aievolution.rag;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BM25 稀疏向量编码器（W16 #2 A3-lite 核心）：分词 → 词项哈希 → 权重。
 *
 * <p>FastEmbed 同款分解口径：文档侧 value 只含 TF 饱和 + 长度归一（{@code f*(k1+1)/(f +
 * k1*(1-b+b*dl/avgdl))}），<strong>不含 IDF</strong>——IDF 由 Qdrant 服务端 sparse 向量的 IDF modifier
 * 在查询时自动补上（collection 级维护，upsert 即更新）。应用侧因此零 IDF 账簿， ADR-0017 当年给候选 A 记的最大成本（自维护 IDF 统计表 + 摄入漂移）消解。
 *
 * <p>查询侧 value 全 1.0（词频对查询无意义），去重保序；分词规则由 {@link SparseTokenizer} 单点收口（A11-1），与覆盖率裁决同源。
 *
 * <p>哈希：murmur3 x86_32（seed 0）——token → uint32 稀疏位下标，摄入/查询两侧同算法即对齐； 锚定向量由独立 Python 实现交叉验证（见
 * Bm25EncoderTest）。
 */
public class Bm25Encoder {

  private final double k1;
  private final double b;

  public Bm25Encoder(double k1, double b) {
    this.k1 = k1;
    this.b = b;
  }

  /** BM25 稀疏向量：Qdrant 无关的领域类型——indices 为 uint32 词项位，values 与之一一对齐。 */
  public record SparseVector(int[] indices, float[] values) {}

  /** 文档侧编码：TF 饱和 + 长度归一权重（不含 IDF）。avgDocLength 为语料平均词项数。 */
  public SparseVector encodeDocument(String text, double avgDocLength) {
    List<String> terms = SparseTokenizer.bm25Terms(text);
    Map<String, Integer> tf = new LinkedHashMap<>();
    terms.forEach(t -> tf.merge(t, 1, Integer::sum));
    double dl = terms.size();
    int[] indices = new int[tf.size()];
    float[] values = new float[tf.size()];
    int i = 0;
    for (Map.Entry<String, Integer> e : tf.entrySet()) {
      double f = e.getValue();
      double weight = f * (k1 + 1) / (f + k1 * (1 - b + b * dl / avgDocLength));
      indices[i] = hash(e.getKey());
      values[i] = (float) weight;
      i++;
    }
    return new SparseVector(indices, values);
  }

  /** 查询侧编码：去重保序，权重全 1.0（IDF 由服务端 modifier 补）。 */
  public SparseVector encodeQuery(String text) {
    Map<String, Boolean> unique = new LinkedHashMap<>();
    SparseTokenizer.bm25Terms(text).forEach(t -> unique.putIfAbsent(t, Boolean.TRUE));
    int[] indices = new int[unique.size()];
    float[] values = new float[unique.size()];
    int i = 0;
    for (String term : unique.keySet()) {
      indices[i] = hash(term);
      values[i] = 1.0f;
      i++;
    }
    return new SparseVector(indices, values);
  }

  /** murmur3 x86_32（seed 0）：公开算法，跨语言可复现——索引两侧对齐不依赖 JVM 私有行为。 */
  static int hash(String term) {
    byte[] data = term.getBytes(StandardCharsets.UTF_8);
    int h1 = 0;
    final int c1 = 0xcc9e2d51;
    final int c2 = 0x1b873593;
    int i = 0;
    while (i + 4 <= data.length) {
      int k1 =
          (data[i] & 0xff)
              | ((data[i + 1] & 0xff) << 8)
              | ((data[i + 2] & 0xff) << 16)
              | ((data[i + 3] & 0xff) << 24);
      k1 *= c1;
      k1 = Integer.rotateLeft(k1, 15);
      k1 *= c2;
      h1 ^= k1;
      h1 = Integer.rotateLeft(h1, 13);
      h1 = h1 * 5 + 0xe6546b64;
      i += 4;
    }
    int k1 = 0;
    int remaining = data.length & 3;
    if (remaining == 3) {
      k1 ^= (data[i + 2] & 0xff) << 16;
    }
    if (remaining >= 2) {
      k1 ^= (data[i + 1] & 0xff) << 8;
    }
    if (remaining >= 1) {
      k1 ^= data[i] & 0xff;
      k1 *= c1;
      k1 = Integer.rotateLeft(k1, 15);
      k1 *= c2;
      h1 ^= k1;
    }
    h1 ^= data.length;
    h1 ^= h1 >>> 16;
    h1 *= 0x85ebca6b;
    h1 ^= h1 >>> 13;
    h1 *= 0xc2b2ae35;
    h1 ^= h1 >>> 16;
    return h1;
  }
}
