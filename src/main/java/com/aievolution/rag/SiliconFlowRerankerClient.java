package com.aievolution.rag;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * {@link RerankerClient} 的 SiliconFlow 实现：薄封装 {@code POST /v1/rerank} （bge-reranker-v2-m3，协议探针实证
 * 2026-09-17，非 OpenAI 兼容）。
 *
 * <p>复用 {@code spring.ai.openai.*} 同一份 SiliconFlow 凭证与 base-url（A5 零新增密钥面）； 只在 {@code
 * ai.rag.rerank.enabled=true} 时装配；空 key 构造即抛（fail-fast， W12 鉴权同一立场——凭证问题不该到第一次调用才炸）。
 *
 * <p>成本留痕：每次调用记录 {@code meta.tokens.input_tokens}——rerank 按输入 token 计费， 黄金集回归的用量即从此日志汇总（#3 一页账口径）。
 */
@Component
@ConditionalOnProperty(name = "ai.rag.rerank.enabled", havingValue = "true")
public class SiliconFlowRerankerClient implements RerankerClient {

  private static final Logger log = LoggerFactory.getLogger(SiliconFlowRerankerClient.class);

  private final RestClient restClient;
  private final String model;

  public SiliconFlowRerankerClient(
      RestClient.Builder restClientBuilder,
      @Value("${spring.ai.openai.base-url:https://api.siliconflow.cn/v1}") String baseUrl,
      @Value("${spring.ai.openai.api-key:}") String apiKey,
      @Value("${ai.rag.rerank.model:BAAI/bge-reranker-v2-m3}") String model) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalArgumentException(
          "rerank 已启用但 SiliconFlow API Key 为空（spring.ai.openai.api-key）");
    }
    this.restClient =
        restClientBuilder
            .baseUrl(baseUrl)
            .defaultHeader("Authorization", "Bearer " + apiKey)
            .build();
    this.model = model;
  }

  @Override
  public List<Document> rerank(String query, List<Document> candidates, int topN) {
    if (candidates.isEmpty()) {
      return List.of();
    }
    RerankRequest request =
        new RerankRequest(model, query, candidates.stream().map(Document::getText).toList(), topN);
    RerankResponse response;
    try {
      response =
          restClient
              .post()
              .uri("/rerank")
              .contentType(MediaType.APPLICATION_JSON)
              .body(request)
              .retrieve()
              .body(RerankResponse.class);
    } catch (Exception e) {
      throw new RerankException("rerank 供应商调用失败: " + e.getMessage(), e);
    }
    if (response == null || response.results() == null) {
      throw new RerankException("rerank 响应为空或缺 results 字段", null);
    }
    if (response.meta() != null && response.meta().tokens() != null) {
      log.info(
          "stage=RERANK usage inputTokens={} candidates={} topN={}",
          response.meta().tokens().inputTokens(),
          candidates.size(),
          topN);
    }
    return response.results().stream()
        .map(
            r -> {
              Document original = candidates.get(r.index());
              return original.mutate().score(r.relevanceScore()).build();
            })
        .toList();
  }

  /** 请求体（top_n 蛇形字段与端点对齐）。 */
  record RerankRequest(
      String model, String query, List<String> documents, @JsonProperty("top_n") int topN) {}

  /** 响应体（relevance_score 蛇形；document 字段为 null 不回传，按 index 映射）。 */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record RerankResponse(List<Result> results, Meta meta) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Result(int index, @JsonProperty("relevance_score") double relevanceScore) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Meta(Tokens tokens) {
      @JsonIgnoreProperties(ignoreUnknown = true)
      record Tokens(@JsonProperty("input_tokens") int inputTokens) {}
    }
  }
}
