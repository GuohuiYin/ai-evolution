package com.aievolution.chat;

import com.aievolution.rag.KnowledgeRetriever;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
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
  private static final String DISCLAIMER = "\n\n——以上由 AI 基于知识库生成，不构成投资建议。";
  private static final String NO_KNOWLEDGE_REPLY = "知识库中未找到与问题相关的资料。为避免误导，我不凭空作答；请先补充相关文档再提问。";

  // prompt 是资产不是字符串常量：模板存 resources/prompts/rag-chat-v1.md，版本化随 git 管理
  private static final String PROMPT_NAME = "rag-chat-v1";

  private final ChatClient chatClient;
  private final KnowledgeRetriever knowledgeRetriever;
  private final PromptLibrary promptLibrary;

  public RagChatService(
      ChatClient.Builder chatClientBuilder,
      KnowledgeRetriever knowledgeRetriever,
      PromptLibrary promptLibrary) {
    this.chatClient = chatClientBuilder.build();
    this.knowledgeRetriever = knowledgeRetriever;
    this.promptLibrary = promptLibrary;
  }

  @Override
  public ChatAnswer chat(String message) {
    List<Document> docs = knowledgeRetriever.retrieve(message);
    if (docs.isEmpty()) {
      return new ChatAnswer(NO_KNOWLEDGE_REPLY + DISCLAIMER, List.of());
    }
    Prompt prompt =
        new PromptTemplate(promptLibrary.get(PROMPT_NAME))
            .create(Map.of("context", joinContents(docs), "question", message));
    String reply = chatClient.prompt(prompt).call().content();
    return new ChatAnswer(reply + DISCLAIMER, toSources(docs));
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
