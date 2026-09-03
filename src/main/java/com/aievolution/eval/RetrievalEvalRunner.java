package com.aievolution.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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
 * 检索评估执行器：本地手动触发（{@code ai.eval.enabled=true}），用真实 bge-m3 跑全量黄金集并输出报告。
 *
 * <p>用法：{@code AI_EVAL_ENABLED=true ./mvnw spring-boot:run} （环境变量经 relaxed binding 映射到
 * ai.eval.enabled）。
 */
@Component
@ConditionalOnProperty(name = "ai.eval.enabled", havingValue = "true")
public class RetrievalEvalRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(RetrievalEvalRunner.class);

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
        new RetrievalEvaluator(vectorStore, similarityThreshold).evaluate(goldenSet);

    log.info("════════ 检索 eval 报告（阈值 {}） ════════", similarityThreshold);
    results.forEach(
        r ->
            log.info(
                "{} [{}] 期望={} 实际={} （{}）",
                r.pass() ? "✅" : "❌",
                r.goldenCase().query(),
                r.goldenCase().expectSource() == null ? "不召回" : r.goldenCase().expectSource(),
                r.actualSource() == null ? "未召回" : r.actualSource(),
                r.pass() ? "通过" : "未通过"));
    long passed = results.stream().filter(RetrievalEvaluator.EvalResult::pass).count();
    log.info("命中率：{}/{}（{}%）", passed, results.size(), Math.round(100.0 * passed / results.size()));
  }
}
