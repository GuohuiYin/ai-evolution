package com.aievolution.loop.protocol;

import org.springframework.stereotype.Component;

/** Action（规范标记）。协议原文，见系统 prompt 输出协议段。 */
@Component
record ActionMarker() implements ProtocolMarker {

  @Override
  public MarkerKind kind() {
    return MarkerKind.ACTION;
  }

  @Override
  public String alias() {
    return "Action";
  }
}
