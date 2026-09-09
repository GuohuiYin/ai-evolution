package com.aievolution.infra;

import org.springframework.ai.chat.client.ChatClientCustomizer;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 模型观测全局挂载（W7-Step2）：{@link TokenUsageAdvisor} 经 ChatClientCustomizer 织入所有
 * ChatClient.Builder——全通路（RAG/Agent/分析）统一记账，单点注册。
 *
 * <p>W8-6：{@link SimpleLoggerAdvisor} 记录模型调用的输入 prompt 与返回 response。开关 {@code
 * ai.chat.prompt-logging.enabled} 默认开（本地调试友好）；生产关闭（金融语料敏感，日志可能含检索到的 年报原文）。日志输出走 DEBUG 级别，logger 名
 * {@code org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor}，MDC traceId 随日志 pattern
 * 串联。
 */
@Configuration
public class ModelObservabilityConfig {

  @Bean
  public ChatClientCustomizer tokenUsageCustomizer() {
    return builder -> builder.defaultAdvisors(new TokenUsageAdvisor());
  }

  @Bean
  @ConditionalOnProperty(
      name = "ai.chat.prompt-logging.enabled",
      havingValue = "true",
      matchIfMissing = true)
  public ChatClientCustomizer promptLoggingCustomizer() {
    return builder -> builder.defaultAdvisors(new SimpleLoggerAdvisor());
  }
}
