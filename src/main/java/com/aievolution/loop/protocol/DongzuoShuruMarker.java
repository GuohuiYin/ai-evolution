package com.aievolution.loop.protocol;

import org.springframework.stereotype.Component;

/**
 * 动作输入（Action Input 中文别名）。证据：W19 rerun2 批 WARN 现场 （docs/eval/w19-rotation-first-run-review.md 第四节
 * #4）。
 */
@Component
record DongzuoShuruMarker() implements ProtocolMarker {

  @Override
  public MarkerKind kind() {
    return MarkerKind.ACTION_INPUT;
  }

  @Override
  public String alias() {
    return "动作输入";
  }
}
