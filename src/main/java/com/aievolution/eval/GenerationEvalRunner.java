package com.aievolution.eval;

import com.aievolution.chat.ChatService;
import com.aievolution.prompt.PromptLibrary;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 生成层评估执行器（W8-4）：本地手动触发（{@code ai.eval.generation.enabled=true}）， 黄金集问题经生产统一入口取回答，LLM judge 按三维
 * rubric 评分，输出实测明细与分类统计。
 *
 * <p>用法：{@code AI_EVAL_GENERATION_ENABLED=true ./mvnw spring-boot:run}。 成本提示：每条用例 = 1 次生成 + 1 次
 * judge 评分，全量 16 条约 32 次模型调用，计入成本账。
 */
@Component
@ConditionalOnProperty(name = "ai.eval.generation.enabled", havingValue = "true")
public class GenerationEvalRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(GenerationEvalRunner.class);

  private final ChatService chatService;
  private final ChatModel chatModel;
  private final PromptLibrary promptLibrary;
  private final String goldenSetLocation;
  private final String categoriesFilter;

  public GenerationEvalRunner(
      ChatService chatService,
      ChatModel chatModel,
      PromptLibrary promptLibrary,
      @Value("${ai.eval.generation.golden-set:eval/golden-set-generation.json}")
          String goldenSetLocation,
      // 类别过滤（逗号分隔，空=全量）：A/B 实验只跑相关类别，控制 token 成本
      @Value("${ai.eval.generation.categories:}") String categoriesFilter) {
    this.chatService = chatService;
    this.chatModel = chatModel;
    this.promptLibrary = promptLibrary;
    this.goldenSetLocation = goldenSetLocation;
    this.categoriesFilter = categoriesFilter;
  }

  @Override
  public void run(ApplicationArguments args) throws Exception {
    List<GenerationCase> goldenSet =
        new ObjectMapper()
            .readerForListOf(GenerationCase.class)
            .readValue(new ClassPathResource(goldenSetLocation).getInputStream());
    if (!categoriesFilter.isBlank()) {
      List<String> allowed = List.of(categoriesFilter.split(","));
      goldenSet = goldenSet.stream().filter(c -> allowed.contains(c.category())).toList();
    }

    // judge 就地构建：组件条件装配，未启用时不占 bean 容器
    GenerationEvaluator evaluator =
        new GenerationEvaluator(chatService, new LlmEvalJudge(chatModel, promptLibrary));
    List<GenerationEvaluator.GenEvalResult> results = evaluator.evaluate(goldenSet);

    log.info("════════ 生成 eval 报告（{} 条，三维各 0-2 分） ════════", results.size());
    results.forEach(
        r ->
            log.info(
                "[{}][{}] 得分={}/{}/{}（总 {}）理由={}\n  回答：{}",
                r.goldenCase().category(),
                r.goldenCase().query(),
                r.score().accuracy(),
                r.score().compliance(),
                r.score().grounding(),
                r.score().total(),
                r.score().reason(),
                r.answer()));

    Map<String, List<GenerationEvaluator.GenEvalResult>> byCategory =
        results.stream()
            .collect(
                Collectors.groupingBy(
                    r -> r.goldenCase().category(), TreeMap::new, Collectors.toList()));
    byCategory.forEach(
        (category, rs) -> {
          double avg = rs.stream().mapToInt(r -> r.score().total()).average().orElse(0);
          log.info("类别 {}：平均总分 {}/6（{} 条）", category, String.format("%.1f", avg), rs.size());
        });
    double overall = results.stream().mapToInt(r -> r.score().total()).average().orElse(0);
    log.info("════════ 总体平均：{}/6 ════════", String.format("%.1f", overall));
  }
}
