package com.aievolution;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// 提供占位 api-key：模型 starter 在 key 缺失时拒绝启动上下文，该测试只验证装配不发真实请求；
// initialize-schema=false：不在测试环境连接真实 Qdrant（容器链路由 *IT 覆盖）
@SpringBootTest(
    properties = {
      "spring.ai.deepseek.api-key=test-key",
      "spring.ai.openai.api-key=test-key",
      "spring.ai.vectorstore.qdrant.initialize-schema=false",
      "ai.knowledge.ingest.enabled=false",
      // W12 #1：测试上下文不起 uvx 外部子进程（CI 无 uvx），MCP Client 链路由专门测试覆盖
      "spring.ai.mcp.client.enabled=false"
    })
class AiEvolutionApplicationTests {

  @Test
  void contextLoads() {}
}
