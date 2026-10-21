package com.aievolution.loop.protocol;

import org.springframework.stereotype.Component;

/** Action Input（规范标记）。协议原文，见系统 prompt 输出协议段。 */
@Component
record ActionInputMarker() implements ProtocolMarker {

  @Override
  public MarkerKind kind() {
    return MarkerKind.ACTION_INPUT;
  }

  @Override
  public String alias() {
    return "Action Input";
  }
}
