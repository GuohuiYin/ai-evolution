package com.aievolution.loop.protocol;

import org.springframework.stereotype.Component;

/**
 * 思考（Thought 中文别名）。证据：W19 六批日志 7 次 protocol violation WARN 全部以其开场
 * （docs/eval/w19-rotation-first-run-review.md 第四节 #4）。
 */
@Component
record SikaoMarker() implements ProtocolMarker {

  @Override
  public MarkerKind kind() {
    return MarkerKind.THOUGHT;
  }

  @Override
  public String alias() {
    return "思考";
  }
}
