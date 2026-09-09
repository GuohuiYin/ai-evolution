package com.aievolution.infra;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientCustomizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

/** W8-6 配置锁定：prompt/response 日志开关必须显式声明，且 Advisor 定制器已织入全局。 */
@SpringBootTest(
    properties = {
      "spring.ai.deepseek.api-key=test-key",
      "spring.ai.openai.api-key=test-key",
      "spring.ai.vectorstore.qdrant.initialize-schema=false",
      "ai.knowledge.ingest.enabled=false"
    })
class PromptLoggingConfigTest {

  @Autowired private Environment environment;

  @Autowired private ApplicationContext context;

  @Test
  void promptLoggingSwitchMustBeExplicitlyConfigured() {
    assertThat(environment.getProperty("ai.chat.prompt-logging.enabled"))
        .as("ai.chat.prompt-logging.enabled 必须显式配置（金融语料敏感，不允许隐式默认漂移）")
        .isNotNull();
  }

  @Test
  void promptLoggingCustomizerIsRegisteredWhenEnabled() {
    assertThat(context.containsBean("promptLoggingCustomizer"))
        .as("开关开启时 SimpleLoggerAdvisor 定制器应注册")
        .isTrue();
    assertThat(context.getBean("promptLoggingCustomizer", ChatClientCustomizer.class)).isNotNull();
  }
}
