package com.aievolution.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

/** {@link LatestAsOfPrioritizer} 单元测试：最新口径优先重排的四种形态。 */
class LatestAsOfPrioritizerTest {

  private final LatestAsOfPrioritizer prioritizer = new LatestAsOfPrioritizer(true);

  private static Document doc(String text, String asOf) {
    return asOf == null
        ? new Document(text, Map.of("docType", "note"))
        : new Document(text, Map.of("docType", "report", "asOf", asOf));
  }

  @Test
  void noYearQueryReordersNewestAsOfFirst() {
    List<Document> hits =
        List.of(
            doc("2024 年报口径", "2024-12-31"),
            doc("2026 半年报口径", "2026-06-30"),
            doc("2025 年报口径", "2025-12-31"));

    List<Document> result = prioritizer.prioritize("茅台的总股本是多少股", hits);

    assertThat(result.stream().map(Document::getText))
        .containsExactly("2026 半年报口径", "2025 年报口径", "2024 年报口径");
  }

  @Test
  void yearPinnedQueryKeepsOriginalOrder() {
    List<Document> hits = List.of(doc("2024 年报口径", "2024-12-31"), doc("2026 半年报口径", "2026-06-30"));

    List<Document> result = prioritizer.prioritize("茅台 2025 年年报披露的总股本是多少股", hits);

    assertThat(result).isSameAs(hits);
  }

  @Test
  void undatedHitsKeepOriginalOrderAndStayBehind() {
    List<Document> hits =
        List.of(doc("方法论笔记", null), doc("2025 年报口径", "2025-12-31"), doc("2024 年报口径", "2024-12-31"));

    List<Document> result = prioritizer.prioritize("茅台的总股本是多少股", hits);

    assertThat(result.stream().map(Document::getText))
        .containsExactly("2025 年报口径", "2024 年报口径", "方法论笔记");
  }

  @Test
  void sameAsOfKeepsRecallOrder() {
    List<Document> hits =
        List.of(doc("片段甲", "2025-12-31"), doc("片段乙", "2025-12-31"), doc("旧口径", "2024-12-31"));

    List<Document> result = prioritizer.prioritize("员工总数是多少", hits);

    assertThat(result.stream().map(Document::getText)).containsExactly("片段甲", "片段乙", "旧口径");
  }

  @Test
  void invalidAsOfTreatedAsUndated() {
    List<Document> hits = List.of(doc("坏时点", "2025年报"), doc("好时点", "2024-12-31"));

    List<Document> result = prioritizer.prioritize("员工总数是多少", hits);

    assertThat(result.stream().map(Document::getText)).containsExactly("好时点", "坏时点");
  }

  @Test
  void disabledKeepsOriginalOrder() {
    LatestAsOfPrioritizer off = new LatestAsOfPrioritizer(false);
    List<Document> hits = List.of(doc("旧", "2024-12-31"), doc("新", "2026-06-30"));

    assertThat(off.prioritize("总股本是多少", hits)).isSameAs(hits);
  }

  @Test
  void singleOrEmptyHitsReturnedAsIs() {
    assertThat(prioritizer.prioritize("总股本是多少", List.of())).isEmpty();
    List<Document> one = List.of(doc("唯一", "2024-12-31"));
    assertThat(prioritizer.prioritize("总股本是多少", one)).isSameAs(one);
  }
}
