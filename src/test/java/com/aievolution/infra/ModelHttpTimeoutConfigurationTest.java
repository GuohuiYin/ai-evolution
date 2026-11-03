package com.aievolution.infra;

import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.ReactorClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** W27 #3b：共享 RestClient.Builder 必须被装上带超时的 request factory（挂起变异常的接缝断言）。 */
class ModelHttpTimeoutConfigurationTest {

  @Test
  void customizerInstallsTimeoutBoundRequestFactory() {
    RestClient.Builder builder = mock(RestClient.Builder.class);

    new ModelHttpTimeoutConfiguration()
        .modelHttpTimeouts(Duration.ofSeconds(10), Duration.ofMinutes(3))
        .customize(builder);

    // 接缝即证据：共享 builder 被装上 Reactor 工厂（携带 responseTimeout/connectTimeout）
    verify(builder).requestFactory(isA(ReactorClientHttpRequestFactory.class));
  }
}
