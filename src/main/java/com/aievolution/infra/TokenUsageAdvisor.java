package com.aievolution.infra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.core.Ordered;

/**
 * token 用量观测 Advisor（W7-Step2）：每次模型调用的"账本"留痕。
 *
 * <p>挂法：由 {@code ModelObservabilityConfig} 通过 ChatClientCustomizer 全局挂载， 所有 ChatClient
 * 实例（RAG/Agent/分析三通路）统一记账——单点化（A12）， 新增通路自动获得账本（开闭原则）。
 *
 * <p>观测不能破坏业务：usage 缺失（部分供应商/流式）记 {@code --} 不抛错。 cache_read 键为 W7-Step3（prompt caching 命中率）预留的度量口。
 */
public class TokenUsageAdvisor implements CallAdvisor {

  private static final Logger log = LoggerFactory.getLogger(TokenUsageAdvisor.class);

  @Override
  public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
    ChatClientResponse response = chain.nextCall(request);
    Usage usage = response.chatResponse().getMetadata().getUsage();
    String model = response.chatResponse().getMetadata().getModel();
    // 空 usage（供应商未上报时返回 total=0 的空对象）按"未上报"处理，记 -- 而非误导性的 0
    boolean reported =
        usage != null && usage.getTotalTokens() != null && usage.getTotalTokens() > 0;
    Long cacheRead = reported ? usage.getCacheReadInputTokens() : null;
    log.info(
        "stage=MODEL model={} tokens_in={} tokens_out={} cache_read={}",
        model == null || model.isBlank() ? "--" : model,
        reported ? usage.getPromptTokens() : "--",
        reported ? usage.getCompletionTokens() : "--",
        cacheRead == null ? "--" : cacheRead);
    return response;
  }

  @Override
  public String getName() {
    return "TokenUsageAdvisor";
  }

  @Override
  public int getOrder() {
    // 尽量靠近链尾：拿到的是最终实际发往模型的请求与真实响应
    return Ordered.LOWEST_PRECEDENCE - 1;
  }
}
