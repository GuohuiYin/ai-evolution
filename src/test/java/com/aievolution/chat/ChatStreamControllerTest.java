package com.aievolution.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;

/** SSE 流式端点契约测试（W10 #0b）：delta 事件逐段下发，complete 事件收尾带会话与来源。 */
@WebMvcTest(ChatController.class)
class ChatStreamControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private RoutingChatService routingChatService;
  @MockitoBean private AgentChatService agentChatService;

  @Test
  void streamEmitsDeltasThenComplete() throws Exception {
    when(routingChatService.chatStream("你好", "conv-1"))
        .thenReturn(
            Flux.just(
                new ChatStreamPart.Delta("你好"),
                new ChatStreamPart.Delta("，我是助手"),
                new ChatStreamPart.Complete(
                    "conv-1", List.of(new SourceDocument("maotai.md", "片段")))));

    MvcResult started =
        mockMvc
            .perform(
                post("/ai/chat/stream")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .content("{\"message\":\"你好\",\"conversationId\":\"conv-1\"}"))
            .andExpect(status().isOk())
            .andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.request()
                    .asyncStarted())
            .andReturn();

    // SSE 是异步响应：须 asyncDispatch 才能拿到完整事件流
    MvcResult result =
        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch(
                    started))
            .andReturn();

    String body =
        new String(
            result.getResponse().getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    assertThat(body).contains("你好").contains("，我是助手");
    assertThat(body).contains("conv-1").contains("maotai.md");
    // delta 先于 complete
    assertThat(body.indexOf("你好")).isLessThan(body.indexOf("conv-1"));
  }
}
