package com.aievolution.rag;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 主体配额配置（W23 #2）：{@code ai.rag.diversity.enabled} 开关 + {@code subjects} 在库主体词典。
 * 词典与语料同源——语料轮换/新增公司入库时同步维护本清单（防腐：写进摄入检查清单）。
 */
@ConfigurationProperties(prefix = "ai.rag.diversity")
public record RetrievalDiversityProperties(boolean enabled, List<SubjectEntry> subjects) {

  public RetrievalDiversityProperties {
    subjects = subjects == null ? List.of() : List.copyOf(subjects);
  }
}
