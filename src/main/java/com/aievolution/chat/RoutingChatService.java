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
  public ChatAnswer chat(String message) {
    return switch (chatRouter.route(message)) {
      case AGENT -> agentChatService.chat(message);
      case RAG -> ragChatService.chat(message);
    };
  }
}
