package com.aievolution.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

/**
 * sparse 覆盖率裁决：黄金集真实失败/对抗用例驱动——代号类必须放行， 近域陷阱（比亚迪刀片电池）必须拒。所有文档文本先经 {@link
 * FullTextNormalizer}（摄入侧行为）。
 */
class SparseCoverageTest {

  private static final String MAOTAI =
      FullTextNormalizer.normalize("酿造工艺遵循12987流程：一年生产周期、两次投料、九次蒸煮、八次发酵、七次取酒");
  private static final String CATL =
      FullTextNormalizer.normalize("宁德时代神行超充电池，SNE Research口径全球市占率37%，动力电池装车量第一");

  private static Document doc(String id, String text) {
    return Document.builder().id(id).text(text).metadata(Map.of()).build();
  }

  @Test
  void identifierQueryPassesWhenCodeHits() {
    // 黄金集 normal 用例："12987 工艺的具体含义" —— dense 三轮全灭，sparse 必须放行
    assertThat(SparseCoverage.score(FullTextNormalizer.normalize("12987 工艺的具体含义"), MAOTAI))
        .isPositive();
  }

  @Test
  void fakeIdentifierIsRejected() {
    // 假代号：12988 不在文中，整问句拒——代号是硬证据
    assertThat(SparseCoverage.score(FullTextNormalizer.normalize("12988 工艺的具体含义"), MAOTAI))
        .isNegative();
  }

  @Test
  void asciiIdentifierWithPercentHits() {
    // 黄金集 literal 用例："SNE Research 口径 37%"
    assertThat(SparseCoverage.score(FullTextNormalizer.normalize("SNE Research 口径 37%"), CATL))
        .isPositive();
  }

  @Test
  void nearDomainTrapIsRejectedByBigramCoverage() {
    // W8 adversarial 判例："比亚迪刀片电池的技术参数" 对 catl.md——二元组覆盖率 1/10，必须拒
    assertThat(SparseCoverage.score(FullTextNormalizer.normalize("比亚迪刀片电池的技术参数"), CATL))
        .isNegative();
  }

  @Test
  void verbatimCjkPhrasePasses() {
    // 黄金集 literal 用例："两次投料 九次蒸煮 八次发酵 七次取酒"（整 token 子串命中）
    assertThat(SparseCoverage.score(FullTextNormalizer.normalize("两次投料 九次蒸煮 八次发酵 七次取酒"), MAOTAI))
        .isPositive();
  }

  @Test
  void unresolvableReferenceQueryStaysOut() {
    // boundary 指代类不是 sparse 的靶子："谁的风险和原材料价格关系最大" 覆盖率低，拒（留给 rerank）
    assertThat(SparseCoverage.score(FullTextNormalizer.normalize("谁的风险和原材料价格关系最大"), CATL))
        .isNegative();
  }

  @Test
  void rankOrdersByCoverageAndIsDeterministic() {
    List<Document> candidates = List.of(doc("b", CATL), doc("a", MAOTAI));
    List<Document> ranked =
        SparseCoverage.rank(FullTextNormalizer.normalize("12987 工艺的具体含义"), candidates, 5);
    assertThat(ranked).extracting(Document::getId).containsExactly("a");
    // 同输入两次调用输出一致（判罚留痕可复现）
    assertThat(SparseCoverage.rank(FullTextNormalizer.normalize("12987 工艺的具体含义"), candidates, 5))
        .extracting(Document::getId)
        .containsExactly("a");
  }

  @Test
  void blankQueryRejectsEverything() {
    assertThat(SparseCoverage.score("", MAOTAI)).isNegative();
    assertThat(SparseCoverage.score("  ", MAOTAI)).isNegative();
  }
}
