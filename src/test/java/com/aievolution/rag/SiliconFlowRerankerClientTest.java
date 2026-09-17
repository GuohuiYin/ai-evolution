package com.aievolution.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** W13 #3a：SiliconFlow /rerank 薄客户端——请求形状/响应映射/空候选短路/故障抛出全锁定。 */
class SiliconFlowRerankerClientTest {

  private static Document doc(String id, String text) {
    return Document.builder().id(id).text(text).metadata(Map.of("source", id + ".md")).build();
  }

  private SiliconFlowRerankerClient newClient(RestClient.Builder builder) {
    return new SiliconFlowRerankerClient(
        builder, "https://api.siliconflow.cn/v1", "test-key", "BAAI/bge-reranker-v2-m3");
  }

  @Test
  void requestShapeAndResponseMapping() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("https://api.siliconflow.cn/v1/rerank"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer test-key"))
        .andExpect(
            content()
                .json(
                    """
                    {"model":"BAAI/bge-reranker-v2-m3","query":"茅台工艺",
                     "documents":["酿造工艺遵循12987流程","动力电池市占率第一"],"top_n":2}
                    """))
        .andRespond(
            withSuccess(
                """
                {"id":"x","results":[
                  {"index":1,"document":null,"relevance_score":0.61},
                  {"index":0,"document":null,"relevance_score":0.74}],
                 "meta":{"tokens":{"input_tokens":42,"output_tokens":0}}}
                """,
                MediaType.APPLICATION_JSON));

    List<Document> hits =
        newClient(builder)
            .rerank("茅台工艺", List.of(doc("a", "酿造工艺遵循12987流程"), doc("b", "动力电池市占率第一")), 2);

    // 响应按 relevance_score 降序返回：index 映射回原候选，score 承载 reranker 分数
    assertThat(hits).extracting(Document::getId).containsExactly("b", "a");
    assertThat(hits.get(0).getScore()).isEqualTo(0.61);
    assertThat(hits.get(1).getScore()).isEqualTo(0.74);
    assertThat(hits.get(1).getMetadata()).containsEntry("source", "a.md");
    server.verify();
  }

  @Test
  void emptyCandidatesShortCircuitsWithoutHttpCall() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    // 不登记任何期望：发 HTTP 即失败
    assertThat(newClient(builder).rerank("茅台工艺", List.of(), 5)).isEmpty();
    server.verify();
  }

  @Test
  void serverErrorThrowsSoCallerCanDegrade() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server.expect(requestTo("https://api.siliconflow.cn/v1/rerank")).andRespond(withServerError());

    assertThatThrownBy(() -> newClient(builder).rerank("q", List.of(doc("a", "t")), 5))
        .isInstanceOf(RerankerClient.RerankException.class);
  }

  @Test
  void blankApiKeyFailsFast() {
    assertThatThrownBy(
            () ->
                new SiliconFlowRerankerClient(
                    RestClient.builder(), "https://api.siliconflow.cn/v1", " ", "model"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
