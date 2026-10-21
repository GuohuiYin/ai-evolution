package com.aievolution.loop.protocol;

import org.springframework.stereotype.Component;

/** Thought（规范标记）。协议原文，见系统 prompt 输出协议段。 */
@Component
record ThoughtMarker() implements ProtocolMarker {

  @Override
  public MarkerKind kind() {
    return MarkerKind.THOUGHT;
  }

  @Override
  public String alias() {
    return "Thought";
  }
}
