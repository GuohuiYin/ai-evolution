package com.aievolution.skill;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/**
 * 技能注册表（W22 #2）：扫描技能目录（默认 {@code classpath*:skill/*\/SKILL.md}）， 解析 YAML
 * front-matter（name/description/triggers）+ 正文。
 *
 * <p>渐进披露两层的供给方：{@link #metadataLines()} 产出常驻元数据（每技能一行）， {@link #match(String)}
 * 按触发关键词命中后由调用方注入正文——对照现状 prompts/ 的静态全量注入， token 成本从线性膨胀降为按需（设计 §2.1/§2.3）。
 *
 * <p>失败哲学：技能文件畸形（缺 name/description）= 部署事故，启动 fail-fast （PromptLibrary 同款）；目录不存在 = 合法空转。
 */
@Component
public class SkillRegistry {

  private final List<Skill> skills;

  /** 生产装配：扫描技能目录（配置项 {@code ai.skill.location}） */
  @org.springframework.beans.factory.annotation.Autowired
  public SkillRegistry(
      ResourcePatternResolver resolver,
      @Value("${ai.skill.location:classpath*:skill/*/SKILL.md}") String locationPattern) {
    this(load(resolver, locationPattern));
  }

  /** 显式清单构造（测试与空注册表场景） */
  public SkillRegistry(List<Skill> skills) {
    this.skills = List.copyOf(skills);
  }

  /** 空注册表：机制关闭时的零行为占位 */
  public static SkillRegistry empty() {
    return new SkillRegistry(List.of());
  }

  public List<Skill> all() {
    return this.skills;
  }

  /** 元数据层：每技能一行（常驻 system prompt，数十 token 量级） */
  public String metadataLines() {
    StringBuilder sb = new StringBuilder();
    this.skills.forEach(s -> sb.append("- %s：%s%n".formatted(s.name(), s.description())));
    return sb.toString().stripTrailing();
  }

  /** 触发匹配：问题含任一触发关键词即命中（大小写不敏感） */
  public List<Skill> match(String text) {
    if (text == null || text.isBlank()) {
      return List.of();
    }
    String lower = text.toLowerCase(java.util.Locale.ROOT);
    return this.skills.stream()
        .filter(
            s ->
                s.triggers().stream()
                    .anyMatch(t -> lower.contains(t.toLowerCase(java.util.Locale.ROOT))))
        .toList();
  }

  private static List<Skill> load(ResourcePatternResolver resolver, String pattern) {
    Resource[] resources;
    try {
      resources = resolver.getResources(pattern);
    } catch (IOException e) {
      throw new UncheckedIOException("技能目录扫描失败: " + pattern, e);
    }
    List<Skill> loaded = new ArrayList<>();
    for (Resource resource : resources) {
      loaded.add(parse(resource));
    }
    // 名称排序：metadataLines 输出稳定（注入内容可复现，A3 可复现约束）
    loaded.sort(Comparator.comparing(Skill::name));
    return List.copyOf(loaded);
  }

  private static Skill parse(Resource resource) {
    String raw;
    try {
      raw = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("技能文件读取失败: " + resource, e);
    }
    String[] parts = raw.split("(?m)^---\\s*$");
    // parts: [头前空白, front-matter, 正文...]
    if (parts.length < 3) {
      throw new IllegalStateException("技能文件缺 YAML front-matter（--- 包围段）: " + resource);
    }
    Map<String, Object> header = new Yaml().load(parts[1]);
    String name = header == null ? null : (String) header.get("name");
    String description = header == null ? null : (String) header.get("description");
    if (name == null || name.isBlank() || description == null || description.isBlank()) {
      throw new IllegalStateException(
          "技能文件缺必填字段 name/description: %s（已知名称: %s）".formatted(resource, name));
    }
    Object triggers = header.get("triggers");
    List<String> triggerList =
        triggers instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    String body = String.join("---", List.of(parts).subList(2, parts.length)).strip();
    return new Skill(name.trim(), description.trim(), triggerList, body);
  }
}
