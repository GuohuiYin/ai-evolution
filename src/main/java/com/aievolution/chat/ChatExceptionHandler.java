package com.aievolution.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 把上游模型异常翻译为对外稳定的错误语义；上游错误细节只进日志，不外泄给客户端。 */
@RestControllerAdvice
class ChatExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ChatExceptionHandler.class);

  /** 不可重试（鉴权失败、余额不足、参数非法等）→ 502 Bad Gateway */
  @ExceptionHandler(NonTransientAiException.class)
  ResponseEntity<ErrorBody> handleNonTransient(NonTransientAiException ex) {
    log.error("上游模型调用失败（不可重试）: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
        .body(new ErrorBody("upstream_model_error"));
  }

  /** 可重试（限流、超时等）→ 503 Service Unavailable，调用方可稍后重试 */
  @ExceptionHandler(TransientAiException.class)
  ResponseEntity<ErrorBody> handleTransient(TransientAiException ex) {
    log.warn("上游模型调用失败（可重试）: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(new ErrorBody("upstream_model_unavailable"));
  }

  record ErrorBody(String error) {}
}
