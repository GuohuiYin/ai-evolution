package com.aievolution.tool;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

/**
 * CVE-2026-59318 防线锁定：工具解析兜底（resolution fallback）必须显式关闭。
 *
 * <p>背景：2.0.0 及更早版本中，请求级工具列表只是"告知"模型的边界，派发时 {@code DefaultToolCallingManager}
 * 的全局兜底能解析到应用上下文所有已注册工具，提示注入可越权调用。2.0.1 修复版默认 fail-fast，但官方结论是"必须把兜底显式配成 fail-fast 才算修完"——
 * 本测试锁死该配置不被回归（例如升级改默认值、或有人误开）。
 */
@SpringBootTest(
    properties = {
      "spring.ai.deepseek.api-key=test-key",
      "spring.ai.openai.api-key=test-key",
      "spring.ai.vectorstore.qdrant.initialize-schema=false",
      "ai.knowledge.ingest.enabled=false"
    })
class ToolResolutionFailFastTest {

  @Autowired private Environment environment;

  @Autowired private ToolCallingProperties toolCallingProperties;

  @Test
  void resolutionFallbackMustBeExplicitlyConfigured() {
    // 仅吃库默认值不够（未来版本可能为兼容翻回 true）：application.yml 必须显式声明
    assertThat(environment.getProperty("spring.ai.tools.resolution.fallback.enabled"))
        .as("spring.ai.tools.resolution.fallback.enabled 必须显式配置（CVE-2026-59318）")
        .isNotNull();
  }

  @Test
  void resolutionFallbackMustBeDisabled() {
    assertThat(toolCallingProperties.getResolution().getFallback().isEnabled())
        .as("工具解析兜底必须关闭：不可解析的工具名应 fail-fast，禁止回落到全局工具表")
        .isFalse();
  }

  @Test
  void toolCallLimitsMustBeExplicitlyConfigured() {
    // 纵深防御：防模型死循环/被注入诱导反复调工具烧 token（成本攻击），上限必须显式声明
    assertThat(environment.getProperty("spring.ai.tools.limits.max-total-tool-calls"))
        .as("spring.ai.tools.limits.max-total-tool-calls 必须显式配置")
        .isNotNull();
    assertThat(environment.getProperty("spring.ai.tools.limits.on-limit-exceeded"))
        .as("spring.ai.tools.limits.on-limit-exceeded 必须显式配置")
        .isNotNull();
  }

  @Test
  void toolCallLimitsMustBeEffective() {
    assertThat(toolCallingProperties.getLimits().getMaxTotalToolCalls())
        .as("每请求工具调用总次数上限")
        .isEqualTo(10);
    // RETURN_ERROR_RESPONSE 而非 THROW：超限作为错误观察喂回模型，由模型组织措辞答复，
    // 避免未处理异常击穿到用户（与 W7 MCP 堆栈脱敏同一思路）
    assertThat(toolCallingProperties.getLimits().getOnLimitExceeded().name())
        .isEqualTo("RETURN_ERROR_RESPONSE");
  }
}
