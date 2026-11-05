package com.aievolution.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * W28 #1：改写注入标识符护栏——改写器增补的标识符（原查询与对话历史均未出现的 代号/编号/英文词）在输出侧剥除，原查询或历史自带的保留。
 *
 * <p>靶案（案 30）：「茅台的法定代表人是谁」被改写为「贵州茅台（600519）的法定代表人是谁」， 增补的 600519 触发 sparse 路「标识符全中才放行」硬规则（W8
 * 判例防线），不含代码的 正确披露块全斩——护栏在改写输出侧剥除增补标识符，不动 sparse 防线本身。
 */
class RewriteIdentifierGuardTest {

  @Test
  void stripsInjectedStockCodeWithBrackets() {
    String rewritten = "贵州茅台（600519）的法定代表人是谁";
    String result = RewriteIdentifierGuard.stripInjected(rewritten, List.of("茅台的法定代表人是谁"));
    assertThat(result).isEqualTo("贵州茅台的法定代表人是谁");
  }

  @Test
  void keepsIdentifierPresentInOriginalQuery() {
    String rewritten = "贵州茅台 600519 2024 年营业总收入";
    String result = RewriteIdentifierGuard.stripInjected(rewritten, List.of("600519 营收多少", "2024"));
    assertThat(result).isEqualTo("贵州茅台 600519 2024 年营业总收入");
  }

  @Test
  void keepsIdentifierPresentInDialogHistory() {
    // 多轮场景：「那茅台呢」承接「宁德时代 2024 年营收」——2024 来自历史，不算增补
    String rewritten = "贵州茅台 2024 年营业总收入";
    String result =
        RewriteIdentifierGuard.stripInjected(
            rewritten, List.of("那茅台呢", "宁德时代 2024 年营收多少", "1741 亿元"));
    assertThat(result).isEqualTo("贵州茅台 2024 年营业总收入");
  }

  @Test
  void identifierMatchIsCaseInsensitive() {
    String rewritten = "宁德时代 sne 电池技术参数";
    String result = RewriteIdentifierGuard.stripInjected(rewritten, List.of("SNE 电池"));
    assertThat(result).isEqualTo("宁德时代 sne 电池技术参数");
  }

  @Test
  void stripsMultipleInjectedIdentifiers() {
    String rewritten = "贵州茅台（600519）与宁德时代（300750）哪家的品牌壁垒更厚";
    String result = RewriteIdentifierGuard.stripInjected(rewritten, List.of("这两家公司谁的品牌壁垒更厚"));
    assertThat(result).isEqualTo("贵州茅台与宁德时代哪家的品牌壁垒更厚");
  }

  @Test
  void mixedCjkAsciiRunStripsOnlyIdentifierPart() {
    // 粘连形态：标识符与中文同 run 时只剥标识符片段，不伤中文
    String rewritten = "贵州茅台600519的法定代表人";
    String result = RewriteIdentifierGuard.stripInjected(rewritten, List.of("茅台的法定代表人"));
    assertThat(result).isEqualTo("贵州茅台的法定代表人");
  }

  @Test
  void passthroughWhenNothingInjected() {
    String rewritten = "宁德时代 2024 年营业总收入";
    String result = RewriteIdentifierGuard.stripInjected(rewritten, List.of("宁德时代 2024 年营业总收入"));
    assertThat(result).isEqualTo(rewritten);
  }

  @Test
  void blankSourcesStripAllIdentifiers() {
    // 无对照源（防御性兜底）：所有标识符视为增补
    String rewritten = "贵州茅台（600519）2024 年报";
    String result = RewriteIdentifierGuard.stripInjected(rewritten, List.of());
    assertThat(result).isEqualTo("贵州茅台 年报");
  }

  @Test
  void nullOrBlankRewrittenPassesThrough() {
    assertThat(RewriteIdentifierGuard.stripInjected(null, List.of("x"))).isNull();
    assertThat(RewriteIdentifierGuard.stripInjected("  ", List.of("x"))).isEqualTo("  ");
  }
}
