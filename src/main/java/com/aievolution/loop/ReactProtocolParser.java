package com.aievolution.loop;

import com.aievolution.loop.protocol.MarkerKind;
import com.aievolution.loop.protocol.ProtocolMarker;
import com.aievolution.loop.protocol.ProtocolMarkerRegistry;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import org.springframework.stereotype.Component;

/**
 * ReAct 文本协议解析器（W11 #3b-1）：把模型的原始输出解析为 {@link ModelTurn}。
 *
 * <p>协议（经典 ReAct 格式，写进系统 prompt 由模型遵守）：
 *
 * <pre>
 * Thought: &lt;思考&gt;
 * Action: &lt;工具名&gt;
 * Action Input: &lt;单行或多行 JSON&gt;
 * </pre>
 *
 * 或以 {@code Final Answer: &lt;回答&gt;} 收尾（回答可跨行，原样保留）。
 *
 * <p>容错：标记集合由 {@link ProtocolMarkerRegistry} 装配（W20 #1 方案 B）——新漂移别名 = 新增一个 {@link ProtocolMarker}
 * Bean，本类零修改（开闭原则）；仍不合协议则返回空，处置权交调用方。
 */
@Component
public class ReactProtocolParser {

  private final ProtocolMarkerRegistry markers;

  public ReactProtocolParser(ProtocolMarkerRegistry markers) {
    this.markers = markers;
  }

  public Optional<ModelTurn> parse(String raw) {
    // 按标记切分：标记种类 → 其后的内容段（到下一标记或文末）
    Matcher m = markers.pattern().matcher(raw);
    MarkerKind lastKey = null;
    int lastEnd = 0;
    Map<MarkerKind, StringBuilder> segments = new EnumMap<>(MarkerKind.class);
    while (m.find()) {
      if (lastKey != null) {
        segments.get(lastKey).append(raw, lastEnd, m.start());
      }
      lastKey = markers.kindOf(m.group(1));
      lastEnd = m.end();
      segments.putIfAbsent(lastKey, new StringBuilder());
    }
    if (lastKey != null) {
      segments.get(lastKey).append(raw, lastEnd, raw.length());
    }
    String thought = segmentText(segments, MarkerKind.THOUGHT);
    String action = segmentText(segments, MarkerKind.ACTION);
    String actionInput = segmentText(segments, MarkerKind.ACTION_INPUT);
    String finalAnswer = segmentText(segments, MarkerKind.FINAL_ANSWER);

    if (finalAnswer != null && !finalAnswer.isEmpty()) {
      return Optional.of(new ModelTurn.Final(thought == null ? "" : thought, finalAnswer));
    }
    // 格式漂移容差：Action 行内联 JSON 入参（"Action: tool({...})"）——拆出工具名与入参
    if (action != null && actionInput == null) {
      int brace = action.indexOf('{');
      if (brace > 0) {
        actionInput = action.substring(brace).replaceAll("\\)$", "").trim();
        action = action.substring(0, brace).replaceAll("\\($", "").trim();
      }
    }
    if (action != null && !action.isEmpty() && actionInput != null && !actionInput.isEmpty()) {
      return Optional.of(new ModelTurn.Act(thought == null ? "" : thought, action, actionInput));
    }
    return Optional.empty();
  }

  private static String segmentText(Map<MarkerKind, StringBuilder> segments, MarkerKind kind) {
    StringBuilder segment = segments.get(kind);
    return segment == null ? null : segment.toString().trim();
  }
}
