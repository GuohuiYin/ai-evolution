package com.aievolution.prompt;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** {@link PromptLibrary} 的 classpath 实现：从 {@code resources/prompts/} 加载并进程内缓存。 */
@Component
public class ClasspathPromptLibrary implements PromptLibrary {

  private static final String LOCATION_PATTERN = "prompts/%s.md";

  private final Map<String, String> cache = new ConcurrentHashMap<>();

  @Override
  public String get(String name) {
    return cache.computeIfAbsent(name, this::load);
  }

  @Override
  public boolean exists(String name) {
    return new ClassPathResource(LOCATION_PATTERN.formatted(name)).exists();
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
