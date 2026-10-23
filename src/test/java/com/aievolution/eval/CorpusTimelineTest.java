package com.aievolution.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** 语料时点清单测试（W21 #2a）：清单机械生成自 knowledge 语料 meta.json—— 语料轮换时本测试是唯一反腐哨兵（增删文件 → 断言失配）。 */
class CorpusTimelineTest {

  @Test
  void 清单覆盖全部在库语料且带时点() {
    String timeline = CorpusTimeline.load();

    // 8 份语料（2 笔记 + 6 定期报告），轮换时此处必须同步——这正是反腐意图
    assertThat(timeline.lines().count()).isEqualTo(8);
    // W20 揭盲病灶①的确诊案例：2026 半年报必须在清单内（judge 曾误判其"不可能存在"）
    assertThat(timeline).contains("贵州茅台：贵州茅台2026年半年度报告（数据时点 2026-06-30）");
    assertThat(timeline).contains("宁德时代：2026年半年度报告（数据时点 2026-06-30）");
    assertThat(timeline).contains("宁德时代：2024年年度报告（数据时点 2024-12-31）");
    assertThat(timeline).contains("maotai（数据时点 2024-12-31）");
  }

  @Test
  void meta缺asOf字段时failFast() {
    assertThrows(
        IllegalStateException.class, () -> CorpusTimeline.extractAsOf("{\"docType\":\"note\"}"));
  }

  @Test
  void judge模板含时点清单占位符且rubric基线在位() {
    // 防腐对偶锁：prompt 侧占位符与代码侧供给必须同时存在（W21 #2a 输入侧注入的完整性）
    String template = new com.aievolution.prompt.ClasspathPromptLibrary().get("eval-judge-v1");
    assertThat(template).contains("{corpusTimeline}");
    assertThat(template).contains("语料时点清单");
    // rubric 基线锁：三维评分尺结构不动（W21 裁决①冻结输入侧以外）；
    // 候选 c 澄清句（Owner 2026-10-23 裁决落地）：grounding 维补"如实出处标注即合规"——
    // 与 golden W18 C-lite 口径对齐，消 judge 与 golden 的结构性错配
    assertThat(template).contains("## accuracy（准确性）");
    assertThat(template).contains("## compliance（遵循度）");
    assertThat(template).contains("## grounding（溯源完整性）");
    assertThat(template).contains("工具摘要/mock 等如实出处标注，均视为合规");
  }
}
