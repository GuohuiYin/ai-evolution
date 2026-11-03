package com.aievolution.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

/** {@link ChunkEvidenceFormatter} 单元测试：溯源头格式与拼接纪律。 */
class ChunkEvidenceFormatterTest {

  @Test
  void blockCarriesSourceAndAsOf() {
    Document doc =
        new Document(
            "期末总股本 1,250,081,601 股", Map.of("source", "2026半年报.pdf", "asOf", "2026-06-30"));

    String block = ChunkEvidenceFormatter.toContextBlock(doc);

    assertThat(block).startsWith("【来源: 2026半年报.pdf，数据时点: 2026-06-30】").contains("1,250,081,601");
  }

  @Test
  void blockWithoutAsOfShowsSourceOnly() {
    Document doc = new Document("酿造工艺 12987", Map.of("source", "maotai.md"));

    assertThat(ChunkEvidenceFormatter.toContextBlock(doc))
        .startsWith("【来源: maotai.md】")
        .doesNotContain("数据时点");
  }

  @Test
  void missingSourceMarkedUnknown() {
    assertThat(ChunkEvidenceFormatter.toContextBlock(new Document("孤本文本")))
        .startsWith("【来源: unknown】");
  }

  @Test
  void joinKeepsInputOrderWithSeparator() {
    List<Document> docs =
        List.of(
            new Document("甲", Map.of("source", "a.pdf", "asOf", "2026-06-30")),
            new Document("乙", Map.of("source", "b.md")));

    String joined = ChunkEvidenceFormatter.joinForContext(docs);

    assertThat(joined).contains("甲\n---\n【来源: b.md】\n乙");
    assertThat(joined.indexOf("甲")).isLessThan(joined.indexOf("乙"));
  }
}
