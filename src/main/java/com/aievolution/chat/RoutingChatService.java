package com.aievolution.chat;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * 统一对话入口（W6 路由裁决落地）：{@code /ai/chat} 的实际实现。
 *
 * <p>对上层（Controller/对话页）隐藏双通路事实——调用方只面向 {@link ChatService} 接口， 路由决策内聚在 {@link
 * ChatRouter}。{@code @Primary} 使本服务成为 ChatService 的默认注入， 而 {@link RagChatService} / {@link
 * AgentChatService} 仍可按具体类型单独注入 （如 {@code /ai/agent} 显式工具入口保留作测试/调试旁路）。
 */
@Service
@Primary
public class RoutingChatService implements ChatService {

  private final ChatRouter chatRouter;
  private final RagChatService ragChatService;
  private final AgentChatService agentChatService;

  public RoutingChatService(
      ChatRouter chatRouter, RagChatService ragChatService, AgentChatService agentChatService) {
    this.chatRouter = chatRouter;
    this.ragChatService = ragChatService;
    this.agentChatService = agentChatService;
  }

  @Override
  public ChatAnswer chat(String message, String conversationId) {
    return switch (chatRouter.route(message)) {
      case AGENT -> agentChatService.chat(message, conversationId);
      case RAG -> ragChatService.chat(message, conversationId);
    };
  }

  @Override
  public reactor.core.publisher.Flux<ChatStreamPart> chatStream(
      String message, String conversationId) {
    // 路由决策先行，流式委派给命中的通路——路由语义与同步入口完全一致
    return switch (chatRouter.route(message)) {
      case AGENT -> agentChatService.chatStream(message, conversationId);
      case RAG -> ragChatService.chatStream(message, conversationId);
    };
  }
}
