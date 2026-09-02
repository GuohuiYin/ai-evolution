package com.aievolution.chat;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** W1 阶段的健康端点占位：验证工程骨架、探针与部署链路。 真实 ChatClient 接入在 Owner 配置模型 API Key 后的后续步骤完成。 */
@RestController
@RequestMapping("/ai")
public class ChatHealthController {

  @GetMapping("/chat")
  public Map<String, String> health() {
    return Map.of("status", "ok");
  }
}
