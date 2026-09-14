package com.aievolution.eval;

import java.util.List;
import java.util.Map;

/** 生成 eval 回归门（W11 #7）：类别均分低于阈值即违约。纯函数判定——阈值比较逻辑进单测， CI workflow 只负责传阈值与看退出码，不在 shell 里藏判定逻辑。 */
public final class EvalGate {

  private EvalGate() {}

  /**
   * @param categoryAverages 类别 → 平均总分
   * @param minCategoryAverage 类别均分下限（含等于）；负值非法
   * @return 违约类别描述（{@code "类别=均分"}，按字典序，供日志与 issue 直接引用）；空 = 门通过
   */
  public static List<String> breaches(
      Map<String, Double> categoryAverages, double minCategoryAverage) {
    if (minCategoryAverage < 0) {
      throw new IllegalArgumentException("minCategoryAverage 不能为负: " + minCategoryAverage);
    }
    return categoryAverages.entrySet().stream()
        .filter(e -> e.getValue() < minCategoryAverage)
        .map(e -> e.getKey() + "=" + String.format("%.1f", e.getValue()))
        .sorted()
        .toList();
  }
}
