package com.aievolution.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 把上游模型异常翻译为对外稳定的错误语义（RFC 7807 ProblemDetail，约定 B3）； 上游错误细节只进日志，不外泄给客户端。机器可读错误码放在扩展属性 {@code
 * error}。
 */
@RestControllerAdvice
class ChatExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ChatExceptionHandler.class);

  /** 不可重试（鉴权失败、余额不足、参数非法等）→ 502 Bad Gateway */
  @ExceptionHandler(NonTransientAiException.class)
  ResponseEntity<ProblemDetail> handleNonTransient(NonTransientAiException ex) {
    log.error("上游模型调用失败（不可重试）: {}", ex.getMessage());
    return problem(HttpStatus.BAD_GATEWAY, "upstream_model_error", "上游模型调用失败");
  }

  /** 可重试（限流、超时等）→ 503 Service Unavailable，调用方可稍后重试 */
  @ExceptionHandler(TransientAiException.class)
  ResponseEntity<ProblemDetail> handleTransient(TransientAiException ex) {
    log.warn("上游模型调用失败（可重试）: {}", ex.getMessage());
    return problem(HttpStatus.SERVICE_UNAVAILABLE, "upstream_model_unavailable", "上游模型暂不可用");
  }

  /**
   * 模型输出无法解析为约定 schema（结构化输出的固有脆弱性）→ 502。 模型输出不合法本质上是上游故障而非服务端缺陷；W7 再升级为"重试一次+修复提示"的 Correct 机制。
   */
  @ExceptionHandler(tools.jackson.core.exc.StreamReadException.class)
  ResponseEntity<ProblemDetail> handleOutputParse(tools.jackson.core.exc.StreamReadException ex) {
    log.warn("模型输出解析失败（非约定 JSON）: {}", ex.getMessage());
    return problem(HttpStatus.BAD_GATEWAY, "upstream_output_parse_error", "模型输出格式非法");
  }

  /**
   * OpenAI 兼容客户端（SiliconFlow/Qwen 等）的原生 HTTP 错误（如 402 余额不足）→ 502。 Spring AI
   * 的异常包装不覆盖该客户端的原生异常，需单独翻译，保证对外错误语义稳定。
   */
  @ExceptionHandler(com.openai.errors.OpenAIException.class)
  ResponseEntity<ProblemDetail> handleOpenAiClient(com.openai.errors.OpenAIException ex) {
    log.error("上游模型调用失败（OpenAI 兼容通道）: {}", ex.getMessage());
    return problem(HttpStatus.BAD_GATEWAY, "upstream_model_error", "上游模型调用失败");
  }

  /** RFC 7807：title 给人看，扩展属性 error 给程序判断。 */
  private ResponseEntity<ProblemDetail> problem(HttpStatus status, String errorCode, String title) {
    ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, title);
    detail.setTitle(title);
    detail.setProperty("error", errorCode);
    return ResponseEntity.status(status).body(detail);
  }
}
