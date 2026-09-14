package com.aievolution.loop;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * <p>容错：标记行允许 Markdown 加粗（{@code **Thought:**}）与全角冒号—— 模型的格式漂移是常态，解析器严进宽出；仍不合协议则返回空，处置权交调用方。
 */
public class ReactProtocolParser {

  private static final Pattern MARKER =
      Pattern.compile("(?m)^[*\\s]*(Thought|Action Input|Action|Final Answer)[*\\s]*[:：][*\\s]*");

  public Optional<ModelTurn> parse(String raw) {
    // 按标记切分：标记名 → 其后的内容段（到下一标记或文末）
    Matcher m = MARKER.matcher(raw);
    String thought = "";
    String action = null;
    String actionInput = null;
    String finalAnswer = null;
    String lastKey = null;
    int lastEnd = 0;
    java.util.Map<String, StringBuilder> segments = new java.util.LinkedHashMap<>();
    while (m.find()) {
      if (lastKey != null) {
        segments.get(lastKey).append(raw, lastEnd, m.start());
      }
      lastKey = m.group(1);
      lastEnd = m.end();
      segments.putIfAbsent(lastKey, new StringBuilder());
    }
    if (lastKey != null) {
      segments.get(lastKey).append(raw, lastEnd, raw.length());
    }
    if (segments.containsKey("Thought")) {
      thought = segments.get("Thought").toString().trim();
    }
    if (segments.containsKey("Action")) {
      action = segments.get("Action").toString().trim();
    }
    if (segments.containsKey("Action Input")) {
      actionInput = segments.get("Action Input").toString().trim();
    }
    if (segments.containsKey("Final Answer")) {
      finalAnswer = segments.get("Final Answer").toString().trim();
    }

    if (finalAnswer != null && !finalAnswer.isEmpty()) {
      return Optional.of(new ModelTurn.Final(thought, finalAnswer));
    }
    if (action != null && !action.isEmpty() && actionInput != null && !actionInput.isEmpty()) {
      return Optional.of(new ModelTurn.Act(thought, action, actionInput));
    }
    return Optional.empty();
  }
}
