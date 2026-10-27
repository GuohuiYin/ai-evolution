package com.aievolution.feedback;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 人工反馈入口（W24 #3 最小环）：对话页 👍/👎 → badcase 登记入库（快照最后一条问答留证） → 人工裁定后由 scripts/badcase_review.py
 * 回流黄金集候选区。裁决②A：不自动入集。
 */
@RestController
@RequestMapping("/ai/feedback")
public class FeedbackController {

  private final FeedbackStore store;
  private final ChatMemory chatMemory;

  public FeedbackController(FeedbackStore store, ChatMemory chatMemory) {
    this.store = store;
    this.chatMemory = chatMemory;
  }

  @PostMapping
  public ResponseEntity<FeedbackEntry> submit(@Valid @RequestBody FeedbackRequest request) {
    List<Message> history = chatMemory.get(request.conversationId());
    if (history.isEmpty()) {
      // 反馈必须挂在真实存在的会话上——空记忆意味着 conversationId 瞎编或已失忆，登记无靶无意义
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
    // 页面捎了问题原文优先采用（记忆里是 RAG 增强后的巨 prompt，不可读）；没捎才回退记忆快照
    String userMessage =
        request.query() != null && !request.query().isBlank()
            ? request.query()
            : lastText(history, MessageType.USER);
    String assistantAnswer = lastText(history, MessageType.ASSISTANT);
    String status =
        "down".equals(request.rating())
            ? FeedbackEntry.STATUS_PENDING_REVIEW
            : FeedbackEntry.STATUS_NOTED;
    FeedbackEntry entry =
        new FeedbackEntry(
            UUID.randomUUID().toString().substring(0, 8),
            request.conversationId(),
            request.rating(),
            request.comment(),
            userMessage,
            assistantAnswer,
            status,
            Instant.now().toString());
    store.append(entry);
    return ResponseEntity.ok(entry);
  }

  private static String lastText(List<Message> history, MessageType type) {
    for (int i = history.size() - 1; i >= 0; i--) {
      if (history.get(i).getMessageType() == type) {
        return history.get(i).getText();
      }
    }
    return "";
  }
}
