package com.aievolution.compliance;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 红线服务端检测（W7-Step1 P1，红队基线输入）：模型自觉之外的最后一道防线。
 *
 * <p>第一版**只告警不拦截**（stage=REDLINE_HIT 进日志链路）：先积累真实命中数据， 再决定是否硬拦——拦截误伤合规表述的代价高于延迟发现。
 *
 * <p>误报控制：模式固定"建议/应该 → 买卖词"的语序方向，拒答话术（"不能给出买入建议"） 语序相反，天然不命中。
 */
@Component
public class RedLineGuard {

  /** 买卖建议模式：建议类词在前、操作词在后（语序即语义）。 */
  private static final List<Pattern> ADVICE_PATTERNS =
      List.of(
          Pattern.compile("强烈建议"),
          Pattern.compile("建议.{0,8}(立即|马上|现在)?.{0,4}(买入|卖出|全仓|清仓|加仓|减仓)"),
          Pattern.compile("应该.{0,4}(立即)?.{0,2}(买入|卖出)"));

  /**
   * 扫描文本中的买卖建议表述。
   *
   * @return 命中的模式描述列表；空列表 = 未触碰红线
   */
  public List<String> scan(String text) {
    if (text == null || text.isBlank()) {
      return List.of();
    }
    return ADVICE_PATTERNS.stream()
        .filter(p -> p.matcher(text).find())
        .map(Pattern::pattern)
        .toList();
  }
}
