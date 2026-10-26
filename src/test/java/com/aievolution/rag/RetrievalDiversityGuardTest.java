package com.aievolution.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

/**
 * W23 #2b：主体配额重排——双主体查询防 top-k 垄断（病灶：宁德/茅台比价案 top-5 被单一主体 年报片段垄断）。只在已召回集合内重排，绝不发明文档；配额之外的名额按原排名回填。
 */
class RetrievalDiversityGuardTest {

  private static final List<SubjectEntry> DICTIONARY =
      List.of(
          new SubjectEntry("贵州茅台", List.of("茅台", "maotai"), "600519"),
          new SubjectEntry("宁德时代", List.of("宁德", "catl"), "300750"));

  private final RetrievalDiversityGuard guard = new RetrievalDiversityGuard(true, DICTIONARY);
  private final RetrievalDiversityGuard offGuard = new RetrievalDiversityGuard(false, DICTIONARY);

  private static Document catl(int i) {
    return Document.builder()
        .id("catl-" + i)
        .text("catl-" + i)
        .metadata(Map.of("source", "宁德时代：2025年年度报告.pdf"))
        .build();
  }

  private static Document maotai(int i) {
    return Document.builder()
        .id("maotai-" + i)
        .text("maotai-" + i)
        .metadata(Map.of("source", "贵州茅台：贵州茅台2025年年度报告.pdf"))
        .build();
  }

  @Test
  void disabledPassesThroughOriginalCut() {
    List<Document> recalled = List.of(catl(1), catl(2), catl(3), maotai(1), catl(4), maotai(2));
    assertThat(offGuard.apply("宁德时代和贵州茅台哪个好", recalled, 2))
        .containsExactly(recalled.get(0), recalled.get(1));
  }

  @Test
  void singleSubjectKeepsOriginalOrder() {
    List<Document> recalled = List.of(catl(1), maotai(1), catl(2));
    assertThat(guard.apply("宁德时代 2025 年营收", recalled, 3)).isEqualTo(recalled);
  }

  @Test
  void monopolyIsBrokenByQuota() {
    // 病灶复刻：召回 20 条里茅台片段排在 6 名开外，裸截 top-5 全被宁德时代垄断
    List<Document> recalled = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
      recalled.add(catl(i));
    }
    for (int i = 0; i < 4; i++) {
      recalled.add(maotai(i));
    }
    List<Document> result = guard.apply("宁德时代和贵州茅台哪个更值得长期持有", recalled, 5);
    long maotaiCount =
        result.stream()
            .filter(d -> String.valueOf(d.getMetadata().get("source")).contains("贵州茅台"))
            .count();
    assertThat(maotaiCount).isGreaterThanOrEqualTo(2);
    assertThat(result).hasSize(5);
  }

  @Test
  void subjectAbsentFromRecallDegradesGracefully() {
    List<Document> recalled = List.of(catl(1), catl(2), catl(3));
    List<Document> result = guard.apply("宁德时代和贵州茅台哪个好", recalled, 3);
    assertThat(result).containsExactly(recalled.toArray(new Document[0]));
  }

  @Test
  void poolLevelMonopolyIsFixedByTargetedSupplement() {
    // 涉案案实锤形态：召回池 20 条零茅台（buckets=[0,20]）——缺口主体锚定补召救回
    List<Document> recalled = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      recalled.add(catl(i));
    }
    List<Document> result =
        guard.apply(
            "宁德时代和贵州茅台哪个更值得长期持有",
            recalled,
            5,
            anchored -> List.of(maotai(100), maotai(101), catl(200)));
    long maotaiCount =
        result.stream()
            .filter(d -> String.valueOf(d.getMetadata().get("source")).contains("贵州茅台"))
            .count();
    assertThat(maotaiCount).isEqualTo(2);
    assertThat(result).hasSize(5);
  }

  @Test
  void supplementSkipsDocsAlreadyInPoolAndForeignSubjects() {
    // 池内已有 1 条茅台（不足配额 2）→ 补召返回：同 ID 重复 / 别主体 / 新茅台——只有新茅台入桶
    List<Document> recalled = List.of(catl(1), catl(2), catl(3), catl(4), catl(5), maotai(1));
    List<Document> result =
        guard.apply(
            "600519 对比 300750", recalled, 5, anchored -> List.of(maotai(1), catl(99), maotai(2)));
    assertThat(result).contains(maotai(2));
    assertThat(result.stream().filter(d -> d.getId().equals("maotai-1")).count()).isEqualTo(1);
    assertThat(result).hasSize(5);
  }

  @Test
  void supplementAnchorStripsOtherSubjects() {
    // 锚定查询 = 本主体名 + 原查询剔除其他主体词（混合查询 dense 信号被主导方淹没的实证对策）
    List<String> anchors = new ArrayList<>();
    List<Document> recalled = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      recalled.add(catl(i));
    }
    guard.apply(
        "宁德时代和贵州茅台（600519）哪个更值得长期持有",
        recalled,
        5,
        q -> {
          anchors.add(q);
          return List.of(maotai(1));
        });
    assertThat(anchors).isNotEmpty();
    assertThat(anchors.getFirst()).startsWith("贵州茅台").doesNotContain("宁德", "300750", "catl");
  }

  @Test
  void resultIsSubsetOfRecalledAndNeverExceedsTopK() {
    List<Document> recalled = List.of(catl(1), maotai(1), catl(2), maotai(2), catl(3), catl(4));
    List<Document> result = guard.apply("600519 对比 300750", recalled, 4);
    assertThat(result).hasSize(4);
    assertThat(recalled).containsAll(result);
  }
}
