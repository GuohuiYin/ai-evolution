package com.aievolution.chat;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ChatService chatService;

  @Test
  void chatReturnsReplyFromService() throws Exception {
    when(chatService.chat("你好"))
        .thenReturn(
            new ChatAnswer(
                "你好，我是 DeepSeek", java.util.List.of(new SourceDocument("maotai.md", "示例片段"))));

    mockMvc
        .perform(
            post("/ai/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"你好\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reply").value("你好，我是 DeepSeek"))
        .andExpect(jsonPath("$.sources[0].source").value("maotai.md"));
  }

  @Test
  void blankMessageIsRejected() throws Exception {
    mockMvc
        .perform(
            post("/ai/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void nonTransientModelErrorMapsToBadGateway() throws Exception {
    when(chatService.chat("test")).thenThrow(new NonTransientAiException("HTTP 402"));

    mockMvc
        .perform(
            post("/ai/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"test\"}"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.error").value("upstream_model_error"));
  }
}
