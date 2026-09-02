package com.aievolution;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// 提供占位 api-key：模型 starter 在 key 缺失时拒绝启动上下文，该测试只验证装配不发真实请求
@SpringBootTest(properties = "spring.ai.deepseek.api-key=test-key")
class AiEvolutionApplicationTests {

  @Test
  void contextLoads() {}
}
