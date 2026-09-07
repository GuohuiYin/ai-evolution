package com.aievolution.chat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aievolution.chat.ChatRouter.Route;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 统一入口委派测试：路由到 AGENT 走工具通路，路由到 RAG 走检索管道，互不串线。 */
class RoutingChatServiceTest {

  private final ChatRouter router = mock(ChatRouter.class);
  private final RagChatService ragChatService = mock(RagChatService.class);
  private final AgentChatService agentChatService = mock(AgentChatService.class);
  private final RoutingChatService service =
      new RoutingChatService(router, ragChatService, agentChatService);

  @Test
  void agentRouteDelegatesToAgentChatService() {
    when(router.route("600519 营收")).thenReturn(Route.AGENT);
    ChatAnswer expected = new ChatAnswer("工具回答", List.of());
    when(agentChatService.chat("600519 营收")).thenReturn(expected);

    service.chat("600519 营收");

    verify(agentChatService).chat("600519 营收");
    verifyNoInteractions(ragChatService);
  }

  @Test
  void ragRouteDelegatesToRagChatService() {
    when(router.route("酿造工艺")).thenReturn(Route.RAG);
    ChatAnswer expected = new ChatAnswer("检索回答", List.of());
    when(ragChatService.chat("酿造工艺")).thenReturn(expected);

    service.chat("酿造工艺");

    verify(ragChatService).chat("酿造工艺");
    verifyNoInteractions(agentChatService);
  }
}
