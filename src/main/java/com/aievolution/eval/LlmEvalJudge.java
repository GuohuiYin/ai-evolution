package com.aievolution.eval;

import com.aievolution.prompt.PromptLibrary;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;

/**
 * LLM-as-a-Judge（W8-4）：用独立的模型调用按 rubric 给被测回答打分。
 *
 * <p>与被测通路的隔离：使用裸 {@link ChatClient}（仅基于 ChatModel 构建）， 不继承业务 advisor 链（token 记账、日志、红线切面都不该污染评估）。
 */
public class LlmEvalJudge implements EvalJudge {

  private static final String JUDGE_PROMPT = "eval-judge-v1";
  private static final Pattern JSON_FENCE =
      Pattern.compile("```(?:json)?\\s*(\\{.*?})\\s*```", Pattern.DOTALL);

  private final ChatClient judgeClient;
  private final PromptLibrary promptLibrary;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public LlmEvalJudge(ChatModel chatModel, PromptLibrary promptLibrary) {
    this.judgeClient = ChatClient.builder(chatModel).build();
    this.promptLibrary = promptLibrary;
  }

  @Override
  public JudgeScore judge(GenerationCase generationCase, String answer) {
    String template = promptLibrary.get(JUDGE_PROMPT);
    String prompt =
        new PromptTemplate(template)
            .render(
                Map.of(
                    "query", generationCase.query(),
                    "expectedBehavior", generationCase.expectedBehavior(),
                    "referenceAnswer", generationCase.referenceAnswer(),
                    "answer", answer));
    String raw = judgeClient.prompt(prompt).call().content();
    return parse(raw);
  }

  /** 解析 judge 输出：容忍 ```json 围栏，其余不可解析一律 fail-fast。 */
  private JudgeScore parse(String raw) {
    String json = raw == null ? "" : raw.trim();
    Matcher fence = JSON_FENCE.matcher(json);
    if (fence.find()) {
      json = fence.group(1);
    }
    try {
      Map<String, Object> map = objectMapper.readValue(json, Map.class);
      return new JudgeScore(
          ((Number) map.get("accuracy")).intValue(),
          ((Number) map.get("compliance")).intValue(),
          ((Number) map.get("grounding")).intValue(),
          String.valueOf(map.get("reason")));
    } catch (Exception e) {
      throw new IllegalStateException("judge 输出不可解析，评估 fail-fast：" + raw, e);
    }
  }
}
