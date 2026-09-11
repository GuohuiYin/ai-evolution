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

  @MockitoBean private RoutingChatService routingChatService;

  @MockitoBean private AgentChatService agentChatService;

  @Test
  void chatReturnsReplyFromService() throws Exception {
    when(routingChatService.chat("你好", "conv-1"))
        .thenReturn(
            new ChatAnswer(
                "你好，我是 DeepSeek", java.util.List.of(new SourceDocument("maotai.md", "示例片段"))));

    mockMvc
        .perform(
            post("/ai/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"你好\",\"conversationId\":\"conv-1\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reply").value("你好，我是 DeepSeek"))
        .andExpect(jsonPath("$.conversationId").value("conv-1"))
        .andExpect(jsonPath("$.sources[0].source").value("maotai.md"));
  }

  @Test
  void chatGeneratesConversationIdWhenAbsent() throws Exception {
    when(routingChatService.chat(
            org.mockito.ArgumentMatchers.eq("你好"), org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(new ChatAnswer("回复", java.util.List.of()));

    // 不传 conversationId：服务端生成并回传，调用方拿它续聊
    mockMvc
        .perform(
            post("/ai/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"你好\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.conversationId").isNotEmpty());
  }

  @Test
  void blankMessageIsRejected() throws Exception {
    mockMvc
        .perform(
            post("/ai/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void agentReturnsReplyFromAgentService() throws Exception {
    when(agentChatService.chat("茅台2024年营收", "conv-a"))
        .thenReturn(new ChatAnswer("营收1741.44亿元（来源: mock）", java.util.List.of()));

    mockMvc
        .perform(
            post("/ai/agent")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"茅台2024年营收\",\"conversationId\":\"conv-a\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reply").value("营收1741.44亿元（来源: mock）"))
        .andExpect(jsonPath("$.conversationId").value("conv-a"));
  }

  @Test
  void nonTransientModelErrorMapsToBadGateway() throws Exception {
    when(routingChatService.chat(
            org.mockito.ArgumentMatchers.eq("test"), org.mockito.ArgumentMatchers.anyString()))
        .thenThrow(new NonTransientAiException("HTTP 402"));

    mockMvc
        .perform(
            post("/ai/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"test\"}"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.error").value("upstream_model_error"));
  }
}
