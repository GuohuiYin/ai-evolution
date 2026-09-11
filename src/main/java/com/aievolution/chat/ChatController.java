package com.aievolution.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@Tag(name = "Chat", description = "大模型对话能力")
@RestController
@RequestMapping("/ai")
public class ChatController {

  private final RoutingChatService routingChatService;
  private final AgentChatService agentChatService;
  // complete 事件负载序列化：局部静态实例即可，不引入容器装配依赖
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public ChatController(RoutingChatService routingChatService, AgentChatService agentChatService) {
    this.routingChatService = routingChatService;
    this.agentChatService = agentChatService;
  }

  @Operation(
      summary = "发送对话消息",
      description = "统一对话入口：含股票代码/取数关键词自动路由 Agent 工具通路，其余走 RAG 检索管道（ADR-0010）")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "成功返回模型回复"),
    @ApiResponse(responseCode = "400", description = "请求参数非法（如 message 为空或缺失）"),
    @ApiResponse(
        responseCode = "502",
        description = "上游模型错误，不可重试（如余额不足、鉴权失败、模型参数非法）",
        content =
            @Content(
                mediaType = "application/json",
                examples =
                    @ExampleObject(
                        value =
                            "{\"title\":\"上游模型调用失败\",\"status\":502,\"error\":\"upstream_model_error\"}"))),
    @ApiResponse(
        responseCode = "503",
        description = "上游模型暂不可用，可稍后重试（如限流、超时）",
        content =
            @Content(
                mediaType = "application/json",
                examples =
                    @ExampleObject(
                        value =
                            "{\"title\":\"上游模型暂不可用\",\"status\":503,\"error\":\"upstream_model_unavailable\"}")))
  })
  @PostMapping("/chat")
  public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
    // conversationId 缺省时服务端生成：会话身份从入口层就是一等公民（W10）
    String conversationId =
        request.conversationId() == null || request.conversationId().isBlank()
            ? UUID.randomUUID().toString()
            : request.conversationId();
    ChatAnswer answer = routingChatService.chat(request.message(), conversationId);
    return new ChatResponse(answer.reply(), conversationId, answer.sources());
  }

  @Operation(
      summary = "流式对话（SSE）",
      description =
          "与 /ai/chat 同一通路的流式版本：delta 事件逐段下发模型输出，complete 事件收尾（携带 conversationId 与引用来源）。"
              + "免责声明作为最后一个 delta 并入流尾")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "SSE 事件流（text/event-stream）"),
    @ApiResponse(responseCode = "400", description = "请求参数非法（如 message 为空或缺失）")
  })
  @PostMapping(path = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<ServerSentEvent<String>> chatStream(@Valid @RequestBody ChatRequest request) {
    String conversationId =
        request.conversationId() == null || request.conversationId().isBlank()
            ? UUID.randomUUID().toString()
            : request.conversationId();
    return routingChatService
        .chatStream(request.message(), conversationId)
        .map(
            part ->
                switch (part) {
                  case ChatStreamPart.Delta d ->
                      ServerSentEvent.<String>builder().event("delta").data(d.text()).build();
                  case ChatStreamPart.Complete c ->
                      ServerSentEvent.<String>builder().event("complete").data(toJson(c)).build();
                });
  }

  private String toJson(ChatStreamPart.Complete complete) {
    try {
      return OBJECT_MAPPER.writeValueAsString(complete);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("complete 事件序列化失败", e);
    }
  }

  @Operation(summary = "Agent 对话（工具增强）", description = "模型可自主调用行情/财务工具取数后作答；涉及数字时必先调工具，禁止凭记忆报数")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "成功返回模型回复"),
    @ApiResponse(responseCode = "400", description = "请求参数非法（如 message 为空或缺失）"),
    @ApiResponse(responseCode = "502", description = "上游模型错误或输出解析失败"),
    @ApiResponse(responseCode = "503", description = "上游模型暂不可用，可稍后重试")
  })
  @PostMapping("/agent")
  public ChatResponse agent(@Valid @RequestBody ChatRequest request) {
    // 调试旁路：不接会话记忆，保持单轮纯净（conversationId 回传但不产生记忆）
    String conversationId =
        request.conversationId() == null || request.conversationId().isBlank()
            ? UUID.randomUUID().toString()
            : request.conversationId();
    ChatAnswer answer = agentChatService.chat(request.message(), conversationId);
    return new ChatResponse(answer.reply(), conversationId, answer.sources());
  }
}
