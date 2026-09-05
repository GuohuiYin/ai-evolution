package com.aievolution.chat;

import com.aievolution.prompt.PromptLibrary;
import com.aievolution.tool.AnnouncementTools;
import com.aievolution.tool.StockDataTools;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * 工具增强的对话服务（W5）：模型自主决定是否调用行情/财务工具取数。
 *
 * <p>与 {@link RagChatService} 的分工：RAG 走知识库语义检索（通路一），本服务走工具实时取数 （通路二）；Step1b 公告工具接入后，本服务进化为可调度全部能力的
 * Agent 入口。
 */
@Service
public class AgentChatService {

  // 红线 01：免责声明引用全项目单点定义（约定 A12），不各自拷贝
  private static final String DISCLAIMER = "\n\n" + Disclaimers.AI_GENERATED;
  private static final String PROMPT_NAME = "agent-chat-v1";

  private final ChatClient chatClient;
  private final StockDataTools stockDataTools;
  private final AnnouncementTools announcementTools;
  private final PromptLibrary promptLibrary;

  public AgentChatService(
      ChatClient.Builder chatClientBuilder,
      StockDataTools stockDataTools,
      AnnouncementTools announcementTools,
      PromptLibrary promptLibrary) {
    this.chatClient = chatClientBuilder.build();
    this.stockDataTools = stockDataTools;
    this.announcementTools = announcementTools;
    this.promptLibrary = promptLibrary;
  }

  public ChatAnswer chat(String message) {
    String reply =
        chatClient
            .prompt()
            .system(promptLibrary.get(PROMPT_NAME))
            .user(message)
            .tools(stockDataTools, announcementTools)
            .call()
            .content();
    return new ChatAnswer(reply + DISCLAIMER, List.of());
  }
}
