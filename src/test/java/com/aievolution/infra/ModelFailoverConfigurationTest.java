package com.aievolution.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient;

/**
 * 备用臂 HTTP 超时独立成档单测（W31 #1 步骤 3）：定制器把备用臂档的超时真落到 openai-java SDK 的客户端 Builder 上（反射读私有 timeout
 * 字段——SDK 不提供读回 API，绑定正确性只能这样验）。
 */
class ModelFailoverConfigurationTest {

  private static final ArmTimeoutProperties PROPS =
      new ArmTimeoutProperties(
          new ArmTimeoutProperties.ArmBudget(
              Duration.ofMinutes(2), Duration.ofMinutes(10), Duration.ofMinutes(3)),
          new ArmTimeoutProperties.ArmBudget(
              Duration.ofMinutes(6), Duration.ofMinutes(20), Duration.ofMinutes(6)));

  @Test
  void fallbackTimeoutCustomizerAppliesFallbackArmBudget() throws Exception {
    Constructor<SpringAiOpenAiHttpClient.Builder> ctor =
        SpringAiOpenAiHttpClient.Builder.class.getDeclaredConstructor();
    ctor.setAccessible(true);
    SpringAiOpenAiHttpClient.Builder builder = ctor.newInstance();

    ModelFailoverConfiguration.fallbackTimeoutCustomizer(PROPS).customize(builder);

    Field timeoutField = SpringAiOpenAiHttpClient.Builder.class.getDeclaredField("timeout");
    timeoutField.setAccessible(true);
    com.openai.core.Timeout timeout = (com.openai.core.Timeout) timeoutField.get(builder);
    assertThat(timeout).isNotNull();
    // openai-java 的 timeout(Duration) 语义 = 整请求超时；备用臂档 PT6M（W26 Qwen thinking 实账 2 倍余量）
    assertThat(timeout.request()).isEqualTo(Duration.ofMinutes(6));
  }
}
