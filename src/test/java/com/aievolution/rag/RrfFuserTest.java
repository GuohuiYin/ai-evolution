package com.aievolution.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

/**
 * W13 #2a：RRF（Reciprocal Rank Fusion）融合纯函数——只吃排名不吃分数， 两路召回异构分数（余弦 / MatchText 无分）因此天然可比。常数 k
 * 配置化（A11 防拍值）。
 */
class RrfFuserTest {

  private static Document doc(String id) {
    return Document.builder()
        .id(id)
        .text("content-" + id)
        .metadata(Map.of("source", id + ".md"))
        .build();
  }

  @Test
  void docInBothListsIsBoostedAboveSingleListDocs() {
    RrfFuser fuser = new RrfFuser(60);
    List<Document> dense = List.of(doc("a"), doc("b"), doc("c"));
    List<Document> sparse = List.of(doc("c"), doc("d"));

    List<Document> fused = fuser.fuse(dense, sparse);

    // c 两路都中：1/(60+1)+1/(60+3) 高于 a 的 1/(60+1)，必须升到第一
    assertThat(fused.getFirst().getId()).isEqualTo("c");
    assertThat(fused).extracting(Document::getId).containsExactly("c", "a", "b", "d");
  }

  @Test
  void scoreIsReciprocalRankSumWithConfiguredK() {
    RrfFuser fuser = new RrfFuser(10);
    List<Document> fused = fuser.fuse(List.of(doc("x"), doc("y")), List.of(doc("y")));

    // y：dense rank2 + sparse rank1 → 1/12 + 1/11
    assertThat(fused.getFirst().getScore()).isCloseTo(1.0 / 12 + 1.0 / 11, within(1e-9));
    assertThat(fused.getFirst().getId()).isEqualTo("y");
  }

  @Test
  void emptySparseListKeepsDenseOrder() {
    RrfFuser fuser = new RrfFuser(60);
    List<Document> fused = fuser.fuse(List.of(doc("a"), doc("b")), List.of());
    assertThat(fused).extracting(Document::getId).containsExactly("a", "b");
  }

  @Test
  void tiesBreakDeterministicallyById() {
    RrfFuser fuser = new RrfFuser(60);
    // a/b 在两路同排名对称位：融合分相等，输出必须可复现
    List<Document> first = fuser.fuse(List.of(doc("a"), doc("b")), List.of(doc("b"), doc("a")));
    List<Document> second = fuser.fuse(List.of(doc("a"), doc("b")), List.of(doc("b"), doc("a")));
    assertThat(first).extracting(Document::getId).containsExactly("a", "b");
    assertThat(second).extracting(Document::getId).containsExactly("a", "b");
  }

  @Test
  void originalMetadataSurvivesFusion() {
    RrfFuser fuser = new RrfFuser(60);
    List<Document> fused = fuser.fuse(List.of(doc("a")), List.of());
    assertThat(fused.getFirst().getMetadata()).containsEntry("source", "a.md");
  }

  @Test
  void nonPositiveKFailsFast() {
    assertThatThrownBy(() -> new RrfFuser(0)).isInstanceOf(IllegalArgumentException.class);
  }
}
