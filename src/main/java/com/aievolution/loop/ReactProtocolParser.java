package com.aievolution.loop;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 * <p>容错：标记行允许 Markdown 加粗（{@code **Thought:**}）、全角冒号与中文标记别名 （思考/行动/动作/操作/…输入/最终答案）——
 * 模型的格式漂移是常态，解析器严进宽出；仍不合协议则返回空， 处置权交调用方。中文别名来自 W19 六批日志 7 次 protocol violation WARN 的真实漂移现场（W20
 * #1）。
 */
@Component
public class ReactProtocolParser {

  // 别名表按"长标记在前"排列：正则同位置从左到右尝试，「行动输入」必须先于「行动」匹配
  private static final Pattern MARKER =
      Pattern.compile(
          "(?m)^[*\\s]*(Final Answer|最终答案|Action Input|行动输入|动作输入|操作输入|输入"
              + "|Thought|思考|Action|行动|动作|操作)[*\\s]*[:：][*\\s]*");

  /** 中文别名 → 协议规范标记（协议字面量，允许硬编码——A11 常量/策略之辨） */
  private static String canonical(String marker) {
    return switch (marker) {
      case "思考" -> "Thought";
      case "行动", "动作", "操作" -> "Action";
      case "行动输入", "动作输入", "操作输入", "输入" -> "Action Input";
      case "最终答案" -> "Final Answer";
      default -> marker;
    };
  }

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
      lastKey = canonical(m.group(1));
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
    // 格式漂移容差：Action 行内联 JSON 入参（"Action: tool({...})"）——拆出工具名与入参
    if (action != null && actionInput == null) {
      int brace = action.indexOf('{');
      if (brace > 0) {
        actionInput = action.substring(brace).replaceAll("\\)$", "").trim();
        action = action.substring(0, brace).replaceAll("\\($", "").trim();
      }
    }
    if (action != null && !action.isEmpty() && actionInput != null && !actionInput.isEmpty()) {
      return Optional.of(new ModelTurn.Act(thought, action, actionInput));
    }
    return Optional.empty();
  }
}
