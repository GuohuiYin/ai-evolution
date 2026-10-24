package com.aievolution.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * W22 #2：SkillRegistry——技能目录扫描 + YAML 头解析 + 关键词触发。 渐进披露两层：元数据常驻（{@link
 * SkillRegistry#metadataLines()}）/ 正文命中注入（{@link SkillRegistry#match}）。
 */
class SkillRegistryTest {

  private SkillRegistry registry(String pattern) {
    return new SkillRegistry(new PathMatchingResourcePatternResolver(), pattern);
  }

  @Test
  void loadsSkillsFromClasspathPattern() {
    SkillRegistry registry = registry("classpath*:skill-test/*/SKILL.md");

    assertThat(registry.all())
        .extracting(Skill::name)
        .containsExactlyInAnyOrder("alpha-skill", "beta-skill");
  }

  @Test
  void parsesFrontmatterAndBody() {
    SkillRegistry registry = registry("classpath*:skill-test/*/SKILL.md");

    Skill alpha =
        registry.all().stream()
            .filter(s -> s.name().equals("alpha-skill"))
            .findFirst()
            .orElseThrow();
    assertThat(alpha.description()).isEqualTo("测试技能甲");
    assertThat(alpha.triggers()).containsExactly("苹果", "香蕉");
    assertThat(alpha.body()).contains("甲的正文内容");

    // 正文多行保留
    Skill beta =
        registry.all().stream()
            .filter(s -> s.name().equals("beta-skill"))
            .findFirst()
            .orElseThrow();
    assertThat(beta.body()).contains("含多行");
  }

  @Test
  void missingRequiredFieldFailsFast() {
    // 技能文件畸形 = 部署事故，启动即 fail-fast（PromptLibrary 同款哲学），不带病运行
    assertThatThrownBy(() -> registry("classpath*:skill-broken/*/SKILL.md"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("broken-skill");
  }

  @Test
  void matchesByTriggerKeyword() {
    SkillRegistry registry = registry("classpath*:skill-test/*/SKILL.md");

    List<Skill> hits = registry.match("苹果今年的收成如何");
    assertThat(hits).extracting(Skill::name).containsExactly("alpha-skill");
  }

  @Test
  void matchesMultipleAndNone() {
    SkillRegistry registry = registry("classpath*:skill-test/*/SKILL.md");

    assertThat(registry.match("苹果和营收都问"))
        .extracting(Skill::name)
        .containsExactlyInAnyOrder("alpha-skill", "beta-skill");
    assertThat(registry.match("完全无关的问题")).isEmpty();
  }

  @Test
  void metadataLinesOnePerSkill() {
    SkillRegistry registry = registry("classpath*:skill-test/*/SKILL.md");

    String metadata = registry.metadataLines();
    assertThat(metadata).contains("- alpha-skill：测试技能甲").contains("- beta-skill：测试技能乙");
  }

  @Test
  void emptyDirectoryYieldsEmptyRegistry() {
    // 没有技能是合法状态（机制可空转），不抛异常
    SkillRegistry registry = registry("classpath*:skill-nonexistent/*/SKILL.md");

    assertThat(registry.all()).isEmpty();
    assertThat(registry.metadataLines()).isEmpty();
    assertThat(registry.match("任何问题")).isEmpty();
  }
}
