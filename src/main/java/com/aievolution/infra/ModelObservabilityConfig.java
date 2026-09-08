package com.aievolution.infra;

import org.springframework.ai.chat.client.ChatClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 模型观测全局挂载（W7-Step2）：{@link TokenUsageAdvisor} 经 ChatClientCustomizer 织入所有
 * ChatClient.Builder——全通路（RAG/Agent/分析）统一记账，单点注册。
 */
@Configuration
public class ModelObservabilityConfig {

  @Bean
  public ChatClientCustomizer tokenUsageCustomizer() {
    return builder -> builder.defaultAdvisors(new TokenUsageAdvisor());
  }
}
