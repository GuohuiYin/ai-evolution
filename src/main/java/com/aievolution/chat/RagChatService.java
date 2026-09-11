package com.aievolution.chat;

import com.aievolution.compliance.Disclaimers;
import com.aievolution.prompt.PromptLibrary;
import com.aievolution.rag.KnowledgeRetriever;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 检索增强的 {@link ChatService}：先查知识库，再把资料拼进 Prompt 让模型"看着资料回答"。
 *
 * <p>手动编排（而非 Advisor 黑盒）的原因：引用来源必须随响应返回（金融红线三）， 且检索为空时直接拒答、不调用模型（防幻觉闸门）。
 *
 * <p>金融红线一在此落地：所有回复末尾服务端强制追加免责声明，不依赖模型自觉。
 */
@Service
public class RagChatService implements ChatService {

  private static final int EXCERPT_MAX_LENGTH = 120;
  // 红线 01：免责声明引用全项目单点定义（约定 A12），不各自拷贝
  private static final String DISCLAIMER = "\n\n" + Disclaimers.AI_GENERATED;
  private static final String NO_KNOWLEDGE_REPLY = "知识库中未找到与问题相关的资料。为避免误导，我不凭空作答；请先补充相关文档再提问。";

  // prompt 是资产不是字符串常量：模板存 resources/prompts/，版本化随 git 管理；
  // 模板名配置化（A11）——A/B 实验（如 few-shot 对照）经配置切换，不改代码
  private final String promptName;

  private final ChatClient chatClient;
  private final KnowledgeRetriever knowledgeRetriever;
  private final PromptLibrary promptLibrary;

  public RagChatService(
      ChatClient.Builder chatClientBuilder,
      KnowledgeRetriever knowledgeRetriever,
      PromptLibrary promptLibrary,
      @Value("${ai.rag.chat-prompt:rag-chat-v1}") String promptName,
      ChatMemory chatMemory) {
    if (!promptLibrary.exists(promptName)) {
      // prompt 缺失是部署事故，启动即 fail-fast
      throw new IllegalStateException("RAG prompt 模板不存在: " + promptName);
    }
    // 会话记忆定点挂载（对话域专属，不全局织入——分析与 eval 通路不共享）
    this.chatClient =
        chatClientBuilder
            .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
            .build();
    this.knowledgeRetriever = knowledgeRetriever;
    this.promptLibrary = promptLibrary;
    this.promptName = promptName;
  }

  @Override
  public ChatAnswer chat(String message, String conversationId) {
    List<Document> docs = knowledgeRetriever.retrieve(message);
    if (docs.isEmpty()) {
      return new ChatAnswer(NO_KNOWLEDGE_REPLY + DISCLAIMER, List.of());
    }
    Prompt prompt =
        new PromptTemplate(promptLibrary.get(promptName))
            .create(Map.of("context", joinContents(docs), "question", message));
    String reply =
        chatClient
            .prompt(prompt)
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
            .call()
            .content();
    return new ChatAnswer(reply + DISCLAIMER, toSources(docs));
  }

  @Override
  public reactor.core.publisher.Flux<ChatStreamPart> chatStream(
      String message, String conversationId) {
    List<Document> docs = knowledgeRetriever.retrieve(message);
    if (docs.isEmpty()) {
      // 空检索硬拒答语义流式化：单条拒答 delta + 收尾
      return reactor.core.publisher.Flux.just(
          new ChatStreamPart.Delta(NO_KNOWLEDGE_REPLY + DISCLAIMER),
          new ChatStreamPart.Complete(conversationId, List.of()));
    }
    Prompt prompt =
        new PromptTemplate(promptLibrary.get(promptName))
            .create(Map.of("context", joinContents(docs), "question", message));
    return chatClient
        .prompt(prompt)
        .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
        .stream()
        .content()
        .map(d -> (ChatStreamPart) new ChatStreamPart.Delta(d))
        .concatWithValues(
            // 红线 01：免责声明作为最后一个 delta 并入流尾
            new ChatStreamPart.Delta(DISCLAIMER),
            new ChatStreamPart.Complete(conversationId, toSources(docs)));
  }

  private String joinContents(List<Document> docs) {
    return docs.stream().map(Document::getText).collect(Collectors.joining("\n---\n"));
  }

  private List<SourceDocument> toSources(List<Document> docs) {
    return docs.stream()
        .map(
            doc ->
                new SourceDocument(
                    String.valueOf(doc.getMetadata().getOrDefault("source", "unknown")),
                    excerpt(doc.getText())))
        .distinct()
        .toList();
  }

  private String excerpt(String text) {
    String oneLine = text.replaceAll("\\s+", " ").trim();
    return oneLine.length() <= EXCERPT_MAX_LENGTH
        ? oneLine
        : oneLine.substring(0, EXCERPT_MAX_LENGTH) + "…";
  }
}
