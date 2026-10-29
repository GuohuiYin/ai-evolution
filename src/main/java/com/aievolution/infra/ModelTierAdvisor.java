package com.aievolution.infra;

import com.aievolution.cost.CostChannel;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.core.Ordered;

/**
 * 模型分档 Advisor（W25 #3，裁决②A 规则先行）：按通路（{@link CostChannel#CONTEXT_KEY}， #2a-ii
 * 已埋进请求上下文）把本轮请求切到该通路的配置档位；空档 = 不切—— 默认零行为变化，可关可切（改配置即换档，无需改代码）。
 *
 * <p>单点收口（A12）：档位是横切策略，不在六个调用点各自撒 {@code defaultOptions}—— 一处 advisor 全局挂载，新增通路自动获得分档能力。台账 byModel
 * 维度（#2a-ii） 立即可见分档账目，切档效果可量化。
 *
 * <p>选项安全：在既有 options 上 {@code mutate()} 只改 model 字段（改写器的温度等 调参不丢）；options 非 DeepSeek
 * 类型（异常装配）时跳过切档按原样放行——埋点永不改变业务语义（{@code TraceSpans} 同款约定）。
 */
public class ModelTierAdvisor implements CallAdvisor {

  private static final Logger log = LoggerFactory.getLogger(ModelTierAdvisor.class);

  /** 通路 → 档位模型名（缺失/空值 = 该通路不切档） */
  private final Map<String, String> tiers;

  public ModelTierAdvisor(Map<String, String> tiers) {
    this.tiers = tiers;
  }

  @Override
  public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
    Object channel = request.context().get(CostChannel.CONTEXT_KEY);
    String tier = channel instanceof String s ? tiers.get(s) : null;
    if (tier == null || tier.isBlank()) {
      return chain.nextCall(request);
    }
    ChatOptions current = request.prompt().getOptions();
    ChatOptions tiered =
        (current == null ? DeepSeekChatOptions.builder() : current.mutate()).model(tier).build();
    if (!(tiered instanceof DeepSeekChatOptions)) {
      log.warn("模型分档跳过：options 类型 {} 不支持切档，按原样放行", tiered.getClass().getSimpleName());
      return chain.nextCall(request);
    }
    log.info("stage=TIER channel={} model={}", channel, tier);
    Prompt prompt = new Prompt(request.prompt().getInstructions(), tiered);
    return chain.nextCall(request.mutate().prompt(prompt).build());
  }

  @Override
  public String getName() {
    return "ModelTierAdvisor";
  }

  @Override
  public int getOrder() {
    // 链首：先于预算熔断（-3）——切档只是重写请求，被拒时无副作用
    return Ordered.LOWEST_PRECEDENCE - 4;
  }
}
