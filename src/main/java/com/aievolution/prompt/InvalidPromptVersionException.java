package com.aievolution.prompt;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 请求指定了不存在的 prompt 版本——对外参数非法是 400 客户端错误，不是 500 服务端故障。 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidPromptVersionException extends RuntimeException {

  public InvalidPromptVersionException(String promptVersion) {
    super("不支持的 prompt 版本: " + promptVersion);
  }
}
