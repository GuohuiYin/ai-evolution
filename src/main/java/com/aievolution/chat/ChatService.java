package com.aievolution.chat;

/** 聊天能力的领域端口：对调用方屏蔽具体模型供应商与检索实现。 新增模型（Qwen 等）或横切增强（缓存、限流）通过新增实现/装饰器完成，不改调用方。 */
public interface ChatService {

  /**
   * @param conversationId 会话标识（W10 起一等公民）：同 ID 续聊共享会话记忆；调用方不传时由入口层生成
   */
  ChatAnswer chat(String message, String conversationId);

  /** 流式对话（W10 #0b SSE）。默认实现由同步回答包装而成——只需同步语义的调用方（如 eval） 无需感知流式；对话通路覆写为真实流式输出。 */
  default reactor.core.publisher.Flux<ChatStreamPart> chatStream(
      String message, String conversationId) {
    ChatAnswer answer = chat(message, conversationId);
    return reactor.core.publisher.Flux.just(
        new ChatStreamPart.Delta(answer.reply()),
        new ChatStreamPart.Complete(conversationId, answer.sources()));
  }
}
