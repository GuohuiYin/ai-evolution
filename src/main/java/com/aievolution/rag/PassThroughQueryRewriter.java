package com.aievolution.rag;

import java.util.List;
import org.springframework.ai.chat.messages.Message;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 直通实现（W11 #5）：原样返回，零成本。 用途：① {@code ai.rewrite.enabled=false} 时的关闭档（#6 改写开/关两轮 eval 对比的对照组）； ②
 * 单轮场景无需改写。
 */
@Component
@ConditionalOnProperty(name = "ai.rewrite.enabled", havingValue = "false")
public class PassThroughQueryRewriter implements QueryRewriter {

  @Override
  public String rewrite(String query, List<Message> history) {
    return query;
  }
}
