package com.aievolution.loop.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.aievolution.loop.LoopStep;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * W22 #1d：消息契约四记录（Plan/EvidencePack/DraftAnswer/ReviewVerdict）—— 跨 Temporal activity 边界必须 Jackson
 * 序列化往返无损（HistoryTurn/ModelTurnPayload 压平先例）。
 */
class PipelineContractsTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void planJsonRoundTripWithLowercaseChannels() throws Exception {
    Plan plan =
        new Plan(
            List.of(
                new Plan.SubQuestion("q1", Channel.RAG), new Plan.SubQuestion("q2", Channel.NONE)));

    String json = mapper.writeValueAsString(plan);
    Plan restored = mapper.readValue(json, Plan.class);

    assertThat(restored).isEqualTo(plan);
    // 通路值序列化为小写（与 planner-v1 prompt 的输出约定一致）
    assertThat(json).contains("\"rag\"").contains("\"none\"");
  }

  @Test
  void channelJsonCreatorIsCaseInsensitive() throws Exception {
    Plan restored =
        mapper.readValue("{\"subQuestions\":[{\"q\":\"x\",\"channel\":\"Stock\"}]}", Plan.class);

    assertThat(restored.subQuestions().getFirst().channel()).isEqualTo(Channel.STOCK);
  }

  @Test
  void evidencePackFromLoopSteps() {
    List<LoopStep> steps =
        List.of(
            new LoopStep(0, "t0", "rag-search", "{\"q\":\"分红\"}", "茅台 2024 分红 75%"),
            new LoopStep(1, "t1", "getDailyQuotes", "{\"code\":\"600519\"}", "1700.00"));

    EvidencePack pack = EvidencePack.fromSteps(steps);

    assertThat(pack.items())
        .containsExactly(
            new EvidencePack.EvidenceItem("rag-search", "{\"q\":\"分红\"}", "茅台 2024 分红 75%"),
            new EvidencePack.EvidenceItem("getDailyQuotes", "{\"code\":\"600519\"}", "1700.00"));
  }

  @Test
  void evidencePackJsonRoundTrip() throws Exception {
    EvidencePack pack =
        EvidencePack.fromSteps(List.of(new LoopStep(0, "t", "rag-search", "in", "out")));

    EvidencePack restored = mapper.readValue(mapper.writeValueAsString(pack), EvidencePack.class);

    assertThat(restored).isEqualTo(pack);
  }

  @Test
  void draftAnswerJsonRoundTrip() throws Exception {
    DraftAnswer draft = new DraftAnswer("茅台 2024 归母净利 857 亿");

    DraftAnswer restored = mapper.readValue(mapper.writeValueAsString(draft), DraftAnswer.class);

    assertThat(restored).isEqualTo(draft);
  }

  @Test
  void reviewVerdictFactoriesAndRoundTrip() throws Exception {
    ReviewVerdict pass = ReviewVerdict.pass();
    ReviewVerdict reject = ReviewVerdict.reject("口径串台：中期分红未计入");

    assertThat(pass.isPass()).isTrue();
    assertThat(reject.isPass()).isFalse();
    assertThat(mapper.readValue(mapper.writeValueAsString(reject), ReviewVerdict.class))
        .isEqualTo(reject);
  }

  @Test
  void reviewVerdictIsPassNotSerialized() throws Exception {
    // ModelTurnPayload 教训：isXxx() 会被 Jackson 当属性序列化，回程炸——必须 @JsonIgnore
    String json = mapper.writeValueAsString(ReviewVerdict.pass());

    assertThat(json).contains("\"kind\":\"PASS\"").doesNotContain("\"pass\"");
  }
}
