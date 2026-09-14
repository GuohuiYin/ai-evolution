package com.aievolution.loop;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * ReAct 文本协议解析器（W11 #3b-1）。协议三要素：Thought（必有）→ Action + Action Input（继续研究） 或 Final Answer（收敛）。解析结果空
 * = 不合协议，处置权交调用方（DeepSeekResearchModel 降级直通，见 #3b-2）。
 */
class ReactProtocolParserTest {

  private final ReactProtocolParser parser = new ReactProtocolParser();

  @Test
  void parsesActionTurn() {
    String raw =
        """
        Thought: 用户问股价，需要先查行情
        Action: getDailyQuotes
        Action Input: {"code":"600519","from":"2024-01-01","to":"2024-12-31"}
        """;

    Optional<ModelTurn> turn = parser.parse(raw);

    assertThat(turn)
        .hasValueSatisfying(
            t -> {
              ModelTurn.Act act = (ModelTurn.Act) t;
              assertThat(act.thought()).isEqualTo("用户问股价，需要先查行情");
              assertThat(act.tool()).isEqualTo("getDailyQuotes");
              assertThat(act.input()).contains("\"code\":\"600519\"");
            });
  }

  @Test
  void parsesFinalAnswer() {
    String raw =
        """
        Thought: 数据已齐，可以作答
        Final Answer: 茅台收盘价 1700 元
        """;

    Optional<ModelTurn> turn = parser.parse(raw);

    assertThat(turn)
        .hasValueSatisfying(
            t -> {
              ModelTurn.Final f = (ModelTurn.Final) t;
              assertThat(f.thought()).isEqualTo("数据已齐，可以作答");
              assertThat(f.answer()).isEqualTo("茅台收盘价 1700 元");
            });
  }

  @Test
  void finalAnswerPreservesInnerNewlines() {
    String raw =
        """
        Thought: 整理多点结论
        Final Answer: 第一点：营收增长
        第二点：工艺壁垒
        """;

    ModelTurn.Final f = (ModelTurn.Final) parser.parse(raw).orElseThrow();

    assertThat(f.answer()).contains("第一点：营收增长\n第二点：工艺壁垒");
  }

  @Test
  void toleratesFullWidthColonAndMarkdownBold() {
    String raw =
        """
        **Thought：** 直接作答
        **Final Answer：** 不用查资料
        """;

    assertThat(parser.parse(raw))
        .hasValueSatisfying(
            t -> {
              ModelTurn.Final f = (ModelTurn.Final) t;
              assertThat(f.thought()).isEqualTo("直接作答");
              assertThat(f.answer()).isEqualTo("不用查资料");
            });
  }

  @Test
  void actionWithoutInputIsUnparseable() {
    assertThat(parser.parse("Thought: 查一下\nAction: getDailyQuotes")).isEmpty();
  }

  @Test
  void plainTextAnswerIsUnparseable() {
    // 模型无视协议直接回答 → 空，调用方决定降级策略
    assertThat(parser.parse("茅台的工艺是 12987")).isEmpty();
  }

  @Test
  void thoughtDefaultsToEmptyWhenMissing() {
    String raw =
        """
        Action: searchAnnouncements
        Action Input: {"query":"茅台工艺"}
        """;

    ModelTurn.Act act = (ModelTurn.Act) parser.parse(raw).orElseThrow();

    assertThat(act.thought()).isEmpty();
    assertThat(act.tool()).isEqualTo("searchAnnouncements");
  }

  @Test
  void toleratesInlineJsonArgsOnActionLine() {
    // 格式漂移实锤（W11 #3c 首轮 eval）：模型把入参内联进 Action 行——
    // 不容忍则直通兜底把协议原文当答案（五粮液/冰淇淋/净利润三案归零的根因）
    String raw =
        """
        Thought: 改用 2023 会计年度再查
        Action: getFinancialSummary({"code": "000858", "fiscalYear": 2023})
        """;

    ModelTurn.Act act = (ModelTurn.Act) parser.parse(raw).orElseThrow();

    assertThat(act.tool()).isEqualTo("getFinancialSummary");
    assertThat(act.input()).contains("\"code\": \"000858\"").contains("\"fiscalYear\": 2023");
  }
}
