package com.aievolution.chat;

import com.aievolution.compliance.Disclaimers;
import com.aievolution.prompt.PromptLibrary;
import com.aievolution.tool.AnnouncementTools;
import com.aievolution.tool.StockDataTools;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 工具增强的对话服务（W5）：模型自主决定是否调用行情/财务工具取数。
 *
 * <p>与 {@link RagChatService} 的分工：RAG 走知识库语义检索（通路一），本服务走工具实时取数 （通路二）；Step1b 公告工具接入后，本服务进化为可调度全部能力的
 * Agent 入口。
 */
@Service
public class AgentChatService implements ChatService {

  // 红线 01：免责声明引用全项目单点定义（约定 A12），不各自拷贝
  private static final String DISCLAIMER = "\n\n" + Disclaimers.AI_GENERATED;

  // prompt 是资产不是字符串常量：模板名配置化（A11），与 RagChatService 同模式；
  // 反缝合规则等 prompt 迭代经配置切换版本（W9-2 起 v2），不改代码
  private final String promptName;

  private final ChatClient chatClient;
  private final StockDataTools stockDataTools;
  private final AnnouncementTools announcementTools;
  private final PromptLibrary promptLibrary;

  public AgentChatService(
      ChatClient.Builder chatClientBuilder,
      StockDataTools stockDataTools,
      AnnouncementTools announcementTools,
      PromptLibrary promptLibrary,
      @Value("${ai.agent.chat-prompt:agent-chat-v2}") String promptName) {
    if (!promptLibrary.exists(promptName)) {
      // prompt 缺失是部署事故，启动即 fail-fast
      throw new IllegalStateException("Agent prompt 模板不存在: " + promptName);
    }
    this.chatClient = chatClientBuilder.build();
    this.stockDataTools = stockDataTools;
    this.announcementTools = announcementTools;
    this.promptLibrary = promptLibrary;
    this.promptName = promptName;
  }

  public ChatAnswer chat(String message) {
    String reply =
        chatClient
            .prompt()
            .system(promptLibrary.get(promptName))
            .user(message)
            .tools(stockDataTools, announcementTools)
            .call()
            .content();
    return new ChatAnswer(reply + DISCLAIMER, List.of());
  }
}
