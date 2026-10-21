package com.aievolution.loop.protocol;

import org.springframework.stereotype.Component;

/**
 * 输入（Action Input 中文裸别名）。证据：W19 r2-compound 案 1 WARN 现场 （docs/eval/w19-rotation-first-run-review.md
 * 第四节 #4）。
 *
 * <p>已知误伤面：终答正文中行首「输入：」会被截为标记——要求行首 + 冒号，概率低， 按严进宽出接受。
 */
@Component
record ShuruMarker() implements ProtocolMarker {

  @Override
  public MarkerKind kind() {
    return MarkerKind.ACTION_INPUT;
  }

  @Override
  public String alias() {
    return "输入";
  }
}
