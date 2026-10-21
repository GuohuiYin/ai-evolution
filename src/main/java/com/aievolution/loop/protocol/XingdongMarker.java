package com.aievolution.loop.protocol;

import org.springframework.stereotype.Component;

/**
 * 行动（Action 中文别名）。证据：W19 r2-compound / r2-factual / rerun 批 WARN 现场
 * （docs/eval/w19-rotation-first-run-review.md 第四节 #4）。
 */
@Component
record XingdongMarker() implements ProtocolMarker {

  @Override
  public MarkerKind kind() {
    return MarkerKind.ACTION;
  }

  @Override
  public String alias() {
    return "行动";
  }
}
