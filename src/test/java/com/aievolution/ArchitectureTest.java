package com.aievolution;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 架构约束测试（约定 B2）：把 A9 的域间依赖方向写成会失败的测试。
 *
 * <p>规则数据即文档：ALLOWED_DEPENDENCIES 表就是全工程的依赖方向白名单， 新增跨域依赖必须先改这张表——让"违规"变成一次显式的、可 review 的决策。
 */
class ArchitectureTest {

  /** 域间依赖白名单：key 域只允许依赖 value 列出的兄弟域（领域类型/接口/数据契约）。 */
  private static final Map<String, List<String>> ALLOWED_DEPENDENCIES =
      Map.of(
          "chat", List.of("compliance", "prompt", "rag", "tool"),
          "analysis", List.of("compliance", "prompt", "stock"),
          "tool", List.of("rag", "stock"),
          "eval", List.of("rag"),
          // 以下为零出度域：基础域不依赖任何兄弟域
          "rag", List.of(),
          "stock", List.of(),
          "prompt", List.of(),
          "compliance", List.of());

  @Test
  void domainDependenciesFollowAllowlist() {
    JavaClasses classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.aievolution");

    List<String> domains = List.copyOf(ALLOWED_DEPENDENCIES.keySet());
    for (String domain : domains) {
      List<String> forbidden =
          domains.stream()
              .filter(d -> !d.equals(domain))
              .filter(d -> !ALLOWED_DEPENDENCIES.get(domain).contains(d))
              .toList();
      if (forbidden.isEmpty()) {
        continue;
      }
      ArchRule rule =
          noClasses()
              .that()
              .resideInAPackage("com.aievolution." + domain + "..")
              .should()
              .dependOnClassesThat()
              .resideInAnyPackage(
                  forbidden.stream().map(d -> "com.aievolution." + d + "..").toArray(String[]::new))
              .as("%s 域只允许依赖 %s（约定 A9 依赖方向）".formatted(domain, ALLOWED_DEPENDENCIES.get(domain)));
      rule.check(classes);
    }
  }

  @Test
  void noCyclicDependenciesBetweenDomains() {
    JavaClasses classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.aievolution");

    com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices()
        .matching("com.aievolution.(*)..")
        .should()
        .beFreeOfCycles()
        .check(classes);
  }
}
