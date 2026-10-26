package com.aievolution.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** W23 #2a：主体提取器——规则先行（词典+代码），从查询里识别提到的在库主体。 词典顺序即检测顺序（输出稳定可复现，A3）。 */
class SubjectExtractorTest {

  private final SubjectExtractor extractor =
      new SubjectExtractor(
          List.of(
              new SubjectEntry("贵州茅台", List.of("茅台", "maotai"), "600519"),
              new SubjectEntry("宁德时代", List.of("宁德", "catl"), "300750")));

  @Test
  void detectsTwoSubjectsByName() {
    assertThat(extractor.extract("宁德时代和贵州茅台哪个更值得长期持有")).containsExactly("贵州茅台", "宁德时代");
  }

  @Test
  void detectsByAliasCaseInsensitive() {
    assertThat(extractor.extract("CATL 的产能扩张计划")).containsExactly("宁德时代");
    assertThat(extractor.extract("茅台今年分红多少")).containsExactly("贵州茅台");
  }

  @Test
  void detectsByStockCode() {
    assertThat(extractor.extract("600519 和 300750 对比")).containsExactly("贵州茅台", "宁德时代");
  }

  @Test
  void noMatchReturnsEmpty() {
    assertThat(extractor.extract("什么是市盈率")).isEmpty();
  }
}
