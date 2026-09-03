package com.aievolution.chat;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Chat", description = "大模型对话能力")
@RestController
@RequestMapping("/ai")
public class ChatController {

  private final ChatService chatService;

  public ChatController(ChatService chatService) {
    this.chatService = chatService;
  }

  @Operation(summary = "发送对话消息", description = "将用户消息发送给大模型，返回模型生成的回复")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "成功返回模型回复"),
    @ApiResponse(responseCode = "400", description = "请求参数非法（如 message 为空或缺失）"),
    @ApiResponse(
        responseCode = "502",
        description = "上游模型错误，不可重试（如余额不足、鉴权失败、模型参数非法）",
        content =
            @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = "{\"error\":\"upstream_model_error\"}"))),
    @ApiResponse(
        responseCode = "503",
        description = "上游模型暂不可用，可稍后重试（如限流、超时）",
        content =
            @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = "{\"error\":\"upstream_model_unavailable\"}")))
  })
  @PostMapping("/chat")
  public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
    ChatAnswer answer = chatService.chat(request.message());
    return new ChatResponse(answer.reply(), answer.sources());
  }
}
