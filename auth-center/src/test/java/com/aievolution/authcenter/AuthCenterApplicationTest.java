package com.aievolution.authcenter;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

/** 空壳冒烟（W30 #1 步骤 1）：上下文可装配 + actuator 健康探针可通（K8s 友好最小集，C1）。 */
class AuthCenterApplicationTest extends AbstractAuthCenterTest {

  @Test
  void healthProbeIsUp() throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health"))
            .GET()
            .build();

    HttpResponse<String> response =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"status\":\"UP\"");
  }
}
