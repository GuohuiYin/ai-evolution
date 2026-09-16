package com.aievolution.infra;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.openai.autoconfigure.OpenAiCommonProperties;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 模型降级接线（W12 #4b）：备用模型 = Qwen3.5-35B-A3B（SiliconFlow 托管）， 复用现有 {@code spring.ai.openai.*} 连接配置——与
 * embedding 同供应商，零新增密钥面（A5）。
 *
 * <p>装配形态：{@link FailoverChatModel} 标 @Primary，全工程 ChatModel 注入点（对话/分析/judge）
 * 自动获得降级能力，调用方零改动（装饰器模式，开闭原则）；主力 bean 按名 {@code deepSeekChatModel} 显式指定——DeepSeek
 * 自动装配的回退条件按具体类型（DeepSeekChatModel）判定，与 OpenAiChatModel 不冲突（已在 jar 层核实方法级注解）。
 *
 * <p>注意：凭证必须落在 {@link OpenAiChatOptions} 上而非手工组装 ClientOptions——Spring AI 2.0 的
 * OpenAiChatModel.Builder.build() 会用 options 里的 baseUrl/apiKey 再补建 async
 * 客户端（OpenAiSetup.setupAsyncClient）， options 缺凭证即抛 "At least one credential source must be
 * specified"（jar 层反编译核实）。
 */
@Configuration
@ConditionalOnProperty(
    name = "ai.model.failover.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ModelFailoverConfiguration {

  @Bean
  public OpenAiChatModel qwenFallbackChatModel(
      OpenAiCommonProperties openAiCommonProperties,
      @Value("${ai.model.failover.model:Qwen/Qwen3.5-35B-A3B}") String fallbackModel) {
    // 模型名差异落在 ChatOptions，凭证/endpoint 复用 embedding 同一份 SiliconFlow 配置
    OpenAiChatOptions options =
        OpenAiChatOptions.builder()
            .baseUrl(openAiCommonProperties.getBaseUrl())
            .apiKey(openAiCommonProperties.getApiKey())
            .model(fallbackModel)
            .build();
    return OpenAiChatModel.builder().options(options).build();
  }

  @Bean
  @Primary
  public FailoverChatModel failoverChatModel(
      @Qualifier("deepSeekChatModel") ChatModel primary,
      OpenAiChatModel qwenFallbackChatModel,
      MeterRegistry meterRegistry,
      @Value("${ai.model.failover.model:Qwen/Qwen3.5-35B-A3B}") String fallbackModel) {
    return new FailoverChatModel(primary, qwenFallbackChatModel, meterRegistry, fallbackModel);
  }
}
