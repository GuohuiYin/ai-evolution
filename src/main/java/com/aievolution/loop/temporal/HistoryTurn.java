package com.aievolution.loop.temporal;

import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * 会话历史的 JSON 友好载体（W21 #1②）：Spring AI {@link Message} 层次带类型元数据， 不适合直接跨 Temporal payload 边界，压平为
 * role/content 纯记录对。
 */
public record HistoryTurn(String role, String content) {

  public static List<HistoryTurn> fromMessages(List<Message> messages) {
    return messages.stream()
        .map(m -> new HistoryTurn(m.getMessageType().getValue(), m.getText()))
        .toList();
  }

  /** 还原为 Spring AI Message：user/assistant 两角色之外的一律按 user 兜底（历史只会是这两类） */
  public Message toMessage() {
    return "assistant".equals(this.role)
        ? new AssistantMessage(this.content)
        : new UserMessage(this.content);
  }
}
