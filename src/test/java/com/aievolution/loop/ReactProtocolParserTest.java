package com.aievolution.loop;

import static org.assertj.core.api.Assertions.assertThat;

import com.aievolution.loop.protocol.TestProtocolMarkers;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * ReAct 文本协议解析器（W11 #3b-1）。协议三要素：Thought（必有）→ Action + Action Input（继续研究） 或 Final Answer（收敛）。解析结果空
 * = 不合协议，处置权交调用方（DeepSeekResearchModel 降级直通，见 #3b-2）。
 */
class ReactProtocolParserTest {

  private final ReactProtocolParser parser = TestProtocolMarkers.parser();

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

  // ===== W20 #1：中文标记容错——W19 六批日志 7 次 protocol violation WARN 原文夹具 =====
  // 现场变体：行动/动作/操作 = Action；行动输入/动作输入/操作输入/裸"输入" = Action Input；
  // 案4 思考与行动间空行；案5 中文思考混排英文 Action。原文逐字保留（含全角标点）。

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("chineseMarkerFixtures")
  void parsesChineseMarkerFixtures(
      String caseName, String raw, String expectedThoughtPrefix, String expectedTool) {
    ModelTurn.Act act = (ModelTurn.Act) parser.parse(raw).orElseThrow();

    assertThat(act.thought()).startsWith(expectedThoughtPrefix);
    assertThat(act.tool()).isEqualTo(expectedTool);
    assertThat(act.input()).startsWith("{").endsWith("}");
  }

  static Stream<Arguments> chineseMarkerFixtures() {
    return Stream.of(
        Arguments.of(
            "r2-compound 案1（行动+裸输入）",
            """
            思考：营收数据已获取（3620.13 亿元）。第二部分「第二增长曲线」属于业务/战略信息，需检索知识库公告与研究资料。
            行动：searchAnnouncements
            输入：{"query":"宁德时代的第二增长曲线是什么业务？储能、换电等新业务布局如何？"}
            """,
            "营收数据已获取",
            "searchAnnouncements"),
        Arguments.of(
            "r2-compound 案2（行动+行动输入）",
            """
            思考：已取得 2024 年财务摘要（营收 3620.13 亿元、净利 507.45 亿元），但题目要的是归母净利润，且动力电池全球市占率需检索公司公告/研究资料。先检索市占率相关信息。
            行动：searchAnnouncements
            行动输入：{"query":"宁德时代2024年动力电池全球市占率是多少"}
            """,
            "已取得 2024 年财务摘要",
            "searchAnnouncements"),
        Arguments.of(
            "r2-factual 案3（行动+行动输入）",
            """
            思考：已获取 2024 年归母净利润 862.28 亿元（来源：mock，时点：2025-04-30）。现在要检索贵州茅台 2024 年度利润分配方案，包括每股派发现金红利、分红总额及占归母净利润比例。
            行动：searchAnnouncements
            行动输入：{"query":"贵州茅台2024年年度利润分配方案 每股派发现金红利 分红总额 占归母净利润比例"}
            """,
            "已获取 2024 年归母净利润",
            "searchAnnouncements"),
        Arguments.of(
            "rerun 案4（标记间空行）",
            """
            思考：营收数据已拿到（3620.13 亿元，来源：mock，时点：2025-04-30）。接下来查“第二增长曲线”相关的业务资料，市场普遍指储能电池业务，先用语义检索确认公司公告口径。

            行动：searchAnnouncements
            行动输入：{"query":"宁德时代 2024 年第二增长曲线是什么业务？储能业务发展情况如何？"}
            """,
            "营收数据已拿到",
            "searchAnnouncements"),
        Arguments.of(
            "rerun 案5（中文思考混排英文 Action，思考不得丢失）",
            """
            思考：财务摘要只给出总营收与净利，没有出口/海外营收分项。需要检索公告与研究资料中关于出口营收的信息。
            Action: searchAnnouncements
            Action Input: {"query":"贵州茅台2024年出口营收是多少？海外市场收入构成"}
            """,
            "财务摘要只给出总营收与净利",
            "searchAnnouncements"),
        Arguments.of(
            "rerun2 案6（动作+动作输入）",
            """
            思考：净利润数据已拿到（862.28 亿元，来源 mock，时点 2025-04-30）。分红情况还没查，需要检索公告资料。
            动作：searchAnnouncements
            动作输入：{"query":"贵州茅台 2024 年度利润分配方案 每股分红 派息金额 分红总额"}
            """,
            "净利润数据已拿到",
            "searchAnnouncements"),
        Arguments.of(
            "rerun3 案7（操作+操作输入）",
            """
            思考：用户询问五粮液的营收规模，属于财务数字问题，需先调用财务摘要工具。五粮液股票代码为 000858，先查询最近一个完整会计年度 2024 年的数据。
            操作：getFinancialSummary
            操作输入：{"code":"000858","fiscalYear":2024}
            """,
            "用户询问五粮液的营收规模",
            "getFinancialSummary"));
  }

  @Test
  void parsesChineseFinalAnswer() {
    // 最终答案中文变体：W19 六批未观测到，按计划容错清单防御性覆盖
    String raw =
        """
        思考：数据已齐，可以作答
        最终答案：茅台收盘价 1700 元（来源：mock，时点：2025-04-30）
        """;

    ModelTurn.Final f = (ModelTurn.Final) parser.parse(raw).orElseThrow();

    assertThat(f.thought()).isEqualTo("数据已齐，可以作答");
    assertThat(f.answer()).contains("茅台收盘价 1700 元");
  }

  @Test
  void truncatedChineseOutputDegradesSafely() {
    // 截断漂移：输出在 Action Input 出现前被截断 → 空，走调用方降级（与英文截断同语义）
    String raw = "思考：营收数据已拿到，接下来检索公告资料。\n行动：searchAnnouncements";

    assertThat(parser.parse(raw)).isEmpty();
  }
}
