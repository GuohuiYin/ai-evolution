package com.aievolution.loop.protocol;

import com.aievolution.loop.ReactProtocolParser;
import java.util.List;

/** 测试用协议标记全集：手工装配全部 {@link ProtocolMarker} Bean（不启 Spring 上下文）。 新增标记 Bean 时同步登记于此，行为测试即自动覆盖。 */
public final class TestProtocolMarkers {

  private TestProtocolMarkers() {}

  public static List<ProtocolMarker> all() {
    return List.of(
        new ThoughtMarker(),
        new SikaoMarker(),
        new ActionMarker(),
        new XingdongMarker(),
        new DongzuoMarker(),
        new CaozuoMarker(),
        new ActionInputMarker(),
        new XingdongShuruMarker(),
        new DongzuoShuruMarker(),
        new CaozuoShuruMarker(),
        new ShuruMarker(),
        new FinalAnswerMarker(),
        new ZuizhongDaanMarker());
  }

  public static ProtocolMarkerRegistry registry() {
    return new ProtocolMarkerRegistry(all());
  }

  public static ReactProtocolParser parser() {
    return new ReactProtocolParser(registry());
  }
}
