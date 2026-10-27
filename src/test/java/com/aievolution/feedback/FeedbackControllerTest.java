package com.aievolution.feedback;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 人工反馈入口单测（W24 #3）：👎 登记 PENDING_REVIEW / 👍 留痕 NOTED / 快照最后问答 / 未知会话 404 / 非法 rating 400。 */
@WebMvcTest(FeedbackController.class)
class FeedbackControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private FeedbackStore store;
  @MockitoBean private ChatMemory chatMemory;

  private void stubHistory(String conversationId) {
    when(chatMemory.get(conversationId))
        .thenReturn(
            List.of(
                new UserMessage("茅台的酿造工艺是什么"),
                new AssistantMessage("茅台采用 12987 工艺……"),
                new UserMessage("其中的 12987 是什么含义"),
                new AssistantMessage("12987 指一年周期、两次投料……")));
  }

  @Test
  void downRegistersPendingReviewWithSnapshot() throws Exception {
    stubHistory("conv-1");

    mockMvc
        .perform(
            post("/ai/feedback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"conversationId\":\"conv-1\",\"rating\":\"down\",\"comment\":\"没答到点上\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
        // 未捎原文时回退记忆快照：须取会话最后一条问答（裁定与造数的现场还原依据）
        .andExpect(jsonPath("$.userMessage").value("其中的 12987 是什么含义"))
        .andExpect(jsonPath("$.assistantAnswer").value("12987 指一年周期、两次投料……"));

    verify(store).append(any(FeedbackEntry.class));
  }

  @Test
  void clientSuppliedQueryWinsOverMemorySnapshot() throws Exception {
    stubHistory("conv-1");
    // 记忆里存的是 RAG 增强后的巨 prompt（含资料原文），页面捎来的问题原文必须优先，
    // 否则裁定台/候选区的 query 字段不可读、没法造数
    mockMvc
        .perform(
            post("/ai/feedback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"conversationId\":\"conv-1\",\"rating\":\"down\",\"query\":\"茅台的总股本是多少股\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userMessage").value("茅台的总股本是多少股"));
  }

  @Test
  void upIsNotedOnly() throws Exception {
    stubHistory("conv-1");

    mockMvc
        .perform(
            post("/ai/feedback")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"conversationId\":\"conv-1\",\"rating\":\"up\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("NOTED"));
  }

  @Test
  void unknownConversationIs404() throws Exception {
    when(chatMemory.get("ghost")).thenReturn(List.of());

    mockMvc
        .perform(
            post("/ai/feedback")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"conversationId\":\"ghost\",\"rating\":\"down\"}"))
        .andExpect(status().isNotFound());

    verify(store, never()).append(any());
  }

  @Test
  void invalidRatingIs400() throws Exception {
    mockMvc
        .perform(
            post("/ai/feedback")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"conversationId\":\"conv-1\",\"rating\":\"meh\"}"))
        .andExpect(status().isBadRequest());
  }
}
