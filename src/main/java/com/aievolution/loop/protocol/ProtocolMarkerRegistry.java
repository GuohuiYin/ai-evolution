package com.aievolution.loop.protocol;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 协议标记装配器：把全部 {@link ProtocolMarker} Bean 装配成单一解析正则 + 别名归一表。
 *
 * <p>两条保证由代码强制执行，不靠注释或声明顺序：
 *
 * <ul>
 *   <li><b>长标记在前</b>：别名按长度降序拼入交替列表——「行动输入」必先于「行动」匹配， Bean 声明顺序不影响正确性
 *   <li><b>别名唯一</b>：同一别名登记两次即启动 fail-fast（多 Bean 扩展后重复登记 只能在运行期发现，此处收口）
 * </ul>
 */
@Component
public class ProtocolMarkerRegistry {

  private final Pattern pattern;
  private final Map<String, MarkerKind> byAlias;

  public ProtocolMarkerRegistry(List<ProtocolMarker> markers) {
    Map<String, MarkerKind> byAlias = new HashMap<>();
    for (ProtocolMarker marker : markers) {
      MarkerKind previous = byAlias.putIfAbsent(marker.alias(), marker.kind());
      if (previous != null) {
        throw new IllegalStateException("协议标记别名重复登记: " + marker.alias());
      }
    }
    this.byAlias = Map.copyOf(byAlias);
    String alternation =
        byAlias.keySet().stream()
            .sorted(Comparator.comparingInt(String::length).reversed())
            .map(Pattern::quote)
            .collect(Collectors.joining("|"));
    this.pattern = Pattern.compile("(?m)^[*\\s]*(" + alternation + ")[*\\s]*[:：][*\\s]*");
  }

  /** 标记行匹配正则（多行锚定 + Markdown 加粗 / 全角冒号容差） */
  public Pattern pattern() {
    return pattern;
  }

  /** 别名 → 协议标记种类（入参必为正则捕获组，即已登记别名） */
  public MarkerKind kindOf(String alias) {
    return byAlias.get(alias);
  }
}
