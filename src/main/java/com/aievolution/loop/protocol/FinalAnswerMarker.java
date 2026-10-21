package com.aievolution.loop.protocol;

import org.springframework.stereotype.Component;

/** Final Answer（规范标记）。协议原文，见系统 prompt 输出协议段。 */
@Component
record FinalAnswerMarker() implements ProtocolMarker {

  @Override
  public MarkerKind kind() {
    return MarkerKind.FINAL_ANSWER;
  }

  @Override
  public String alias() {
    return "Final Answer";
  }
}
