package com.aievolution.loop.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.regex.Matcher;
import org.junit.jupiter.api.Test;

/** 协议标记装配器（W20 #1 方案 B）：两条强制保证——长标记在前（与 Bean 声明顺序无关）、 别名重复启动 fail-fast。 */
class ProtocolMarkerRegistryTest {

  @Test
  void longerAliasWinsRegardlessOfDeclarationOrder() {
    // 短别名先声明：装配器必须仍让「行动输入」优先匹配，不被「行动」截胡
    ProtocolMarkerRegistry registry =
        new ProtocolMarkerRegistry(List.of(new XingdongMarker(), new XingdongShuruMarker()));

    Matcher m = registry.pattern().matcher("行动输入：{\"query\":\"茅台\"}");

    assertThat(m.find()).isTrue();
    assertThat(m.group(1)).isEqualTo("行动输入");
    assertThat(registry.kindOf(m.group(1))).isEqualTo(MarkerKind.ACTION_INPUT);
  }

  @Test
  void duplicateAliasFailsFast() {
    List<ProtocolMarker> duplicated = List.of(new XingdongMarker(), new XingdongMarker());

    assertThatThrownBy(() -> new ProtocolMarkerRegistry(duplicated))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("行动");
  }

  @Test
  void regexMetacharsInAliasAreQuoted() {
    // 未来别名若含正则元字符，装配器 quote 包裹逐字匹配
    ProtocolMarker marker =
        new ProtocolMarker() {
          @Override
          public MarkerKind kind() {
            return MarkerKind.THOUGHT;
          }

          @Override
          public String alias() {
            return "Think(ing)";
          }
        };
    ProtocolMarkerRegistry registry = new ProtocolMarkerRegistry(List.of(marker));

    Matcher m = registry.pattern().matcher("Think(ing): 括号别名");

    assertThat(m.find()).isTrue();
    assertThat(m.group(1)).isEqualTo("Think(ing)");
  }
}
