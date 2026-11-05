package com.aievolution.rag;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * W28 #1 改写注入标识符护栏（RewriteIdentifierGuard）：改写输出侧的确定性剥离开关—— 改写器增补的标识符（原查询与对话历史均未出现的代号/编号/英文词）一律剥除，
 * 用户原查询或历史自带的保留。
 *
 * <p>靶案（案 30，设计草案 docs/eval/retrieval/w28-recall-enhancement-design.md）：
 * 「茅台的法定代表人是谁」被改写为「贵州茅台（600519）的法定代表人是谁」——增补的 600519 触发 sparse 路「标识符全中才放行」硬规则（W8
 * 判例防线），不含代码的正确披露块全斩， hybrid 退化为 dense 单路漏检。护栏在改写输出侧剥除增补标识符，sparse 防线本身不动。
 *
 * <p>标识符口径与 sparse 分词对齐（{@link SparseTokenizer#isIdentifier}：含字母或数字）， 抽取用连续 token 正则（含 . _ % / : -
 * 内连字符，"3.5" 视为一个 token 而非 "3"+"5"）。 比对小写化（与 sparse 路标识符小写口径一致）。
 */
final class RewriteIdentifierGuard {

  private RewriteIdentifierGuard() {}

  /** 标识符 token：字母数字段，允许内连字符（版本号/小数/百分比分子同属一个 token）。 */
  private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9]+(?:[._%/:-][A-Za-z0-9]+)*");

  /** 剥离后残留的空括号对（全角/半角），循环清除至稳定。 */
  private static final Pattern EMPTY_BRACKETS = Pattern.compile("[（(\\[【「]\\s*[）)\\]】」]");

  /**
   * 剥除 rewritten 中相对 allowedSources 增补的标识符。
   *
   * @param rewritten 改写输出（null/空白原样返回）
   * @param allowedSources 标识符白名单来源文本（原查询 + 对话历史）；空列表 = 所有标识符视为增补
   * @return 剥离后文本；可能为空白（调用方负责回退，见 DeepSeekQueryRewriter）
   */
  static String stripInjected(String rewritten, List<String> allowedSources) {
    if (rewritten == null || rewritten.isBlank()) {
      return rewritten;
    }
    Set<String> allowed = new HashSet<>();
    for (String source : allowedSources) {
      if (source == null) {
        continue;
      }
      Matcher m = TOKEN.matcher(source);
      while (m.find()) {
        allowed.add(m.group().toLowerCase(Locale.ROOT));
      }
    }

    Matcher m = TOKEN.matcher(rewritten);
    StringBuilder sb = new StringBuilder(rewritten.length());
    int last = 0;
    while (m.find()) {
      if (!allowed.contains(m.group().toLowerCase(Locale.ROOT))) {
        sb.append(rewritten, last, m.start());
        last = m.end();
      }
    }
    if (last == 0) {
      return rewritten; // 无增补：原样返回（不引入空白规整等额外扰动）
    }
    sb.append(rewritten.substring(last));

    String cleaned = sb.toString();
    String prev;
    do {
      prev = cleaned;
      cleaned = EMPTY_BRACKETS.matcher(cleaned).replaceAll("");
    } while (!cleaned.equals(prev));
    return cleaned.replaceAll("\\s+", " ").strip();
  }
}
