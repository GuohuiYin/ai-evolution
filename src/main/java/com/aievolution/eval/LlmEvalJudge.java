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

  /** 语料时点清单（W21 #2a）：类加载时扫描一次，语料静态于单次运行；扫描失败即类初始化失败——eval 语境 fail-fast。 */
  private static final String CORPUS_TIMELINE = CorpusTimeline.load();

  private final ChatClient judgeClient;
  private final PromptLibrary promptLibrary;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public LlmEvalJudge(ChatModel chatModel, PromptLibrary promptLibrary) {
    this.judgeClient = ChatClient.builder(chatModel).build();
    this.promptLibrary = promptLibrary;
  }

  @Override
  public JudgeScore judge(GenerationCase generationCase, String answer) {
    return judge(generationCase, answer, false);
  }

  @Override
  public JudgeScore judge(
      GenerationCase generationCase, String answer, boolean emptyRetrievalRefusal) {
    String template = promptLibrary.get(JUDGE_PROMPT);
    String prompt =
        new PromptTemplate(template)
            .render(
                Map.of(
                    "query",
                    generationCase.query(),
                    "expectedBehavior",
                    generationCase.expectedBehavior(),
                    "referenceAnswer",
                    generationCase.referenceAnswer(),
                    // W21 #2a：输入侧注入语料时点清单（rubric 冻结，Owner 裁决①解禁范围仅限上下文）
                    "corpusTimeline",
                    CORPUS_TIMELINE,
                    // W24 #2：输入侧注入检索信号——设计性拒答（召回为空）与无理由拒答切开
                    "retrievalSignal",
                    emptyRetrievalRefusal ? "empty-retrieval（本次拒答为召回为空的设计性行为）" : "none",
                    "answer",
                    answer));
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
