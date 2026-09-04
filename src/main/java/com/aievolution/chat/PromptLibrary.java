package com.aievolution.chat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Prompt 资产库：prompt 是资产而非字符串常量——统一存 {@code resources/prompts/}， 文件名带版本号（如 {@code rag-chat-v1}），随
 * git 版本管理，运行时加载并缓存。
 *
 * <p>加载失败即快速失败（fail-fast）：prompt 缺失是部署事故而非运行期容错场景。
 */
@Component
public class PromptLibrary {

  private static final String LOCATION_PATTERN = "prompts/%s.md";

  private final Map<String, String> cache = new ConcurrentHashMap<>();

  /** 按名字加载 prompt 模板（不含 .md 后缀），结果进程内缓存。 */
  public String get(String name) {
    return cache.computeIfAbsent(name, this::load);
  }

  private String load(String name) {
    ClassPathResource resource = new ClassPathResource(LOCATION_PATTERN.formatted(name));
    if (!resource.exists()) {
      throw new IllegalStateException("Prompt 不存在: prompts/%s.md".formatted(name));
    }
    try {
      return resource.getContentAsString(StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Prompt 读取失败: " + name, e);
    }
  }
}
