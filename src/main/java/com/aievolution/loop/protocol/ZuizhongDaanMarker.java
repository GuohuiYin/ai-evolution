package com.aievolution.loop.protocol;

import org.springframework.stereotype.Component;

/**
 * 最终答案（Final Answer 中文别名）。防御性覆盖：W19 六批未观测到， 按 W20
 * 计划容错清单先行登记（docs/plans/advanced/w20-leak-fix-and-advisor-source.md #1）。
 */
@Component
record ZuizhongDaanMarker() implements ProtocolMarker {

  @Override
  public MarkerKind kind() {
    return MarkerKind.FINAL_ANSWER;
  }

  @Override
  public String alias() {
    return "最终答案";
  }
}
