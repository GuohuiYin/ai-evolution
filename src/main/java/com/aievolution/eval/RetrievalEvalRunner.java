package com.aievolution.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 检索评估执行器：本地手动触发（{@code ai.eval.enabled=true}），用真实 bge-m3 跑全量黄金集，输出 Recall@5 报告并判定 M1 验收门（Recall@5
 * ≥ {@value #GATE_THRESHOLD}）。
 *
 * <p>用法：{@code AI_EVAL_ENABLED=true ./mvnw spring-boot:run} （环境变量经 relaxed binding 映射到
 * ai.eval.enabled）。topK 与线上检索口径（RagChatService / AnnouncementTools）保持一致——评的就是用的。
 */
@Component
@ConditionalOnProperty(name = "ai.eval.enabled", havingValue = "true")
public class RetrievalEvalRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(RetrievalEvalRunner.class);

  /** M1 验收门：正例 Recall@5 不得低于该值（铁律：不过不进下一阶段） */
  static final double GATE_THRESHOLD = 0.7;

  /** 评估 topK，与 RagChatService.TOP_K / AnnouncementTools.TOP_K 对齐 */
  public static final int TOP_K = 5;

  private final VectorStore vectorStore;
  private final double similarityThreshold;

  public RetrievalEvalRunner(
      VectorStore vectorStore,
      @Value("${ai.rag.similarity-threshold:0.5}") double similarityThreshold) {
    this.vectorStore = vectorStore;
    this.similarityThreshold = similarityThreshold;
  }

  @Override
  public void run(ApplicationArguments args) throws Exception {
    List<GoldenCase> goldenSet =
        new ObjectMapper()
            .readerForListOf(GoldenCase.class)
            .readValue(new ClassPathResource("eval/golden-set.json").getInputStream());

    List<RetrievalEvaluator.EvalResult> results =
        new RetrievalEvaluator(vectorStore, similarityThreshold, TOP_K).evaluate(goldenSet);

    log.info("════════ 检索 eval 报告（Recall@{}，阈值 {}） ════════", TOP_K, similarityThreshold);
    results.forEach(
        r ->
            log.info(
                "{} [{}][{}] 期望={} 实际Top1={} （{}）",
                r.pass() ? "✅" : "❌",
                r.goldenCase().category(),
                r.goldenCase().query(),
                r.goldenCase().expectSource() == null ? "不召回" : r.goldenCase().expectSource(),
                r.topSource() == null ? "未召回" : r.topSource(),
                r.pass() ? "通过" : "未通过"));

    // 按类别分组统计，边界/对抗用例的失败能单独定位而不是被总数稀释
    Map<String, long[]> byCategory =
        results.stream()
            .collect(
                Collectors.groupingBy(
                    r -> r.goldenCase().category(),
                    TreeMap::new,
                    Collectors.teeing(
                        Collectors.filtering(
                            RetrievalEvaluator.EvalResult::pass, Collectors.counting()),
                        Collectors.counting(),
                        (passed, total) -> new long[] {passed, total})));
    byCategory.forEach(
        (category, counts) -> log.info("类别 {}：{}/{} 通过", category, counts[0], counts[1]));

    List<RetrievalEvaluator.EvalResult> positives =
        results.stream().filter(r -> r.goldenCase().expectSource() != null).toList();
    long positivePassed = positives.stream().filter(RetrievalEvaluator.EvalResult::pass).count();
    double recall = positives.isEmpty() ? 0 : (double) positivePassed / positives.size();
    List<RetrievalEvaluator.EvalResult> negatives =
        results.stream().filter(r -> r.goldenCase().expectSource() == null).toList();
    long negativePassed = negatives.stream().filter(RetrievalEvaluator.EvalResult::pass).count();

    log.info(
        "正例 Recall@{}：{}/{}（{}%）",
        TOP_K, positivePassed, positives.size(), Math.round(recall * 100));
    log.info("负例拒答率：{}/{}", negativePassed, negatives.size());
    if (recall >= GATE_THRESHOLD) {
      log.info("════════ M1 验收门：通过（Recall@{} {} ≥ {}） ════════", TOP_K, recall, GATE_THRESHOLD);
    } else {
      log.warn(
          "════════ M1 验收门：未通过（Recall@{} {} < {}），不得进入下一阶段 ════════",
          TOP_K,
          recall,
          GATE_THRESHOLD);
    }
  }
}
