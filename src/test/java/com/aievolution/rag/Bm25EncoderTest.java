package com.aievolution.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/**
 * BM25 编码器单测（A3-lite 核心）：文档侧出 TF 饱和+长度归一权重（不含 IDF—— IDF 由 Qdrant 服务端 modifier 在查询时补上，FastEmbed
 * 同款分解口径），查询侧出全 1.0 权重。 哈希锚定向量由独立 Python 实现交叉生成（murmur3 x86_32, seed 0）。
 */
class Bm25EncoderTest {

  private final Bm25Encoder encoder = new Bm25Encoder(1.5, 0.75);

  @Test
  void documentWeightsFollowBm25TfSaturation() {
    // "aa bb bb"：dl=3，avgdl=3 → 无长度折损；bb(f=2)：2*2.5/(2+1.5)=1.4286；aa(f=1)：1*2.5/(1+1.5)=1.0
    Bm25Encoder.SparseVector v = encoder.encodeDocument("aa bb bb", 3.0);

    assertThat(v.indices()).hasSize(2);
    assertThat(v.indices()).containsExactly(murmur("aa"), murmur("bb"));
    assertThat((double) v.values()[0]).isCloseTo(1.0, within(1e-4));
    assertThat((double) v.values()[1]).isCloseTo(1.4286, within(1e-4));
  }

  @Test
  void longerDocumentGetsLowerWeightForSameTerm() {
    // 长度归一（b=0.75）：语料均值固定，同一词频下长文档权重必须更低——年报长 chunk 天然受抑
    Bm25Encoder.SparseVector shortDoc = encoder.encodeDocument("aa bb", 5.0);
    Bm25Encoder.SparseVector longDoc = encoder.encodeDocument("aa bb cc dd ee ff gg hh", 5.0);

    assertThat((double) longDoc.values()[0]).isLessThan((double) shortDoc.values()[0]);
  }

  @Test
  void queryTermsAreUniqueWithUnitWeights() {
    // 查询侧：词频无意义（FastEmbed 同口径），去重后全 1.0，IDF 由服务端 modifier 补
    Bm25Encoder.SparseVector v = encoder.encodeQuery("茅台 茅台 品牌壁垒");

    assertThat(v.indices()).hasSize(4); // 茅台 + 品牌/牌壁/壁垒
    assertThat(v.values()).containsOnly(1.0f);
    assertThat(v.indices()).contains(murmur("品牌"));
  }

  @Test
  void hashIsDeterministicAndMatchesReferenceVectors() {
    // 锚定向量来自独立 Python 实现（murmur3 x86_32 seed 0）——防"实现即标准"自证
    assertThat(murmur("foo")).isEqualTo(0xf6a5c420);
    assertThat(murmur("hello")).isEqualTo(0x248bfa47);
    assertThat(murmur("")).isEqualTo(0);
    // 同词同 index 是摄入/查询两侧对齐的前提
    assertThat(murmur("茅台")).isEqualTo(murmur("茅台"));
  }

  @Test
  void emptyTextYieldsEmptyVector() {
    Bm25Encoder.SparseVector v = encoder.encodeDocument("  ", 3.0);

    assertThat(v.indices()).isEmpty();
    assertThat(v.values()).isEmpty();
  }

  private static int murmur(String term) {
    return Bm25Encoder.hash(term);
  }
}
