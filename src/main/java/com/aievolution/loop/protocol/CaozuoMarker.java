package com.aievolution.loop.protocol;

import org.springframework.stereotype.Component;

/** 操作（Action 中文别名）。证据：W19 rerun3 批 WARN 现场 （docs/eval/w19-rotation-first-run-review.md 第四节 #4）。 */
@Component
record CaozuoMarker() implements ProtocolMarker {

  @Override
  public MarkerKind kind() {
    return MarkerKind.ACTION;
  }

  @Override
  public String alias() {
    return "操作";
  }
}
