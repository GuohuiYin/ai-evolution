package com.aievolution.rag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;

/**
 * 检索主体多样性配额（W23 #2b，裁决②方案 A：召回后配额重排）。
 *
 * <p>病灶：双主体查询（「宁德时代和贵州茅台哪个更值得长期持有」）召回 20 条里茅台片段排在 6 名开外， 裸截 top-5 全被宁德时代年报片段垄断，另一主体零进上下文。机制：查询提到 ≥2
 * 个在库主体时， 每个主体保底 ⌊topK/n⌋ 个名额（轮流出列保排名公平），剩余名额按原排名回填（含无主体归属文档）。
 *
 * <p>不变量：只在已召回集合内重排，绝不发明文档；主体在召回集中缺席时优雅降级为原序截断； 单主体/无主体查询完全不改变行为。默认关，黄金集回归 + 涉案案复跑双证据达标后启用（数据裁决）。
 */
public class RetrievalDiversityGuard {

  private static final Logger log = LoggerFactory.getLogger(RetrievalDiversityGuard.class);

  private final boolean enabled;
  private final SubjectExtractor extractor;
  private final List<SubjectEntry> dictionary;

  public RetrievalDiversityGuard(boolean enabled, List<SubjectEntry> dictionary) {
    this.enabled = enabled;
    this.dictionary = List.copyOf(dictionary);
    this.extractor = new SubjectExtractor(dictionary);
  }

  /**
   * 配额重排入口（无补召）：返回最终返回口径（≤topK）。 召回池本身垄断时退化为原序截断——池级救济用 {@link #apply(String, List, int,
   * java.util.function.Function)}。
   */
  public List<Document> apply(String query, List<Document> recalled, int topK) {
    return apply(query, recalled, topK, q -> List.of());
  }

  /**
   * 配额重排 + 缺口定向补召（W23 #2 升级路径，涉案案 buckets=[0,20] 实锤池级垄断触发）。
   *
   * <p>主体在召回池内的份额不足配额时，以「主体名 + 原查询」为锚补召一次（dense 向量路）， 把该主体的命中补进桶再重排——不拆查询不动路由，是拆分检索（裁决②方案 B）的轻量变体。
   *
   * @param supplementaryFetch 补召通道：入参锚定查询，出参该查询的召回结果（由调用方带过滤条件）
   */
  public List<Document> apply(
      String query,
      List<Document> recalled,
      int topK,
      java.util.function.Function<String, List<Document>> supplementaryFetch) {
    if (!enabled) {
      return recalled.stream().limit(topK).toList();
    }
    LinkedHashSet<String> subjects = extractor.extract(query);
    if (subjects.size() < 2) {
      return recalled.stream().limit(topK).toList();
    }

    Map<String, List<Document>> buckets = new LinkedHashMap<>();
    subjects.forEach(s -> buckets.put(s, new ArrayList<>()));
    List<Document> pool = new ArrayList<>(recalled);
    java.util.Set<String> poolIds =
        recalled.stream().map(Document::getId).collect(java.util.stream.Collectors.toSet());
    for (Document doc : recalled) {
      String owner = attribute(doc);
      if (owner != null && buckets.containsKey(owner)) {
        buckets.get(owner).add(doc);
      }
    }
    int quota = Math.max(1, topK / subjects.size());
    // 池级垄断救济：桶内不足配额的主体定向补召。锚定查询 = 本主体名 + 原查询剔除其他主体词
    // （probe 实证：混合查询 dense 信号被主导方淹没致补召零收获；纯主体锚定后召回 100% 归位）
    for (String subject : subjects) {
      List<Document> bucket = buckets.get(subject);
      if (bucket.size() >= quota) {
        continue;
      }
      String anchor = subject + " " + stripOtherSubjects(query, subject);
      int needed = quota - bucket.size();
      for (Document doc : supplementaryFetch.apply(anchor)) {
        if (needed == 0) {
          break;
        }
        if (subject.equals(attribute(doc)) && !poolIds.contains(doc.getId())) {
          bucket.add(doc);
          pool.add(doc);
          poolIds.add(doc.getId());
          needed--;
        }
      }
      log.info(
          "stage=RETRIEVE diversity-supplement subject={} fetchedIntoBucket={}",
          subject,
          bucket.size());
    }
    List<Document> result = new ArrayList<>();
    java.util.Set<String> resultIds = new java.util.HashSet<>();
    // 轮流出列：每个主体的第 0/1/.../quota-1 名依次入列，保持各方内部排名
    for (int rank = 0; rank < quota; rank++) {
      for (String subject : subjects) {
        List<Document> bucket = buckets.get(subject);
        if (rank < bucket.size()
            && result.size() < topK
            && resultIds.add(bucket.get(rank).getId())) {
          result.add(bucket.get(rank));
        }
      }
    }
    // 剩余名额按原排名回填（含无主体归属文档与各主体溢出文档；补召文档在池尾）
    for (Document doc : pool) {
      if (result.size() >= topK) {
        break;
      }
      if (resultIds.add(doc.getId())) {
        result.add(doc);
      }
    }
    log.info(
        "stage=RETRIEVE diversity subjects={} quota={} buckets={} returnTop={} query={}",
        subjects,
        quota,
        buckets.values().stream().map(List::size).toList(),
        result.size(),
        query.replaceAll("\\s+", " "));
    return result;
  }

  /** 锚定查询构造：剔除其他主体的规范名/别名/代码，保留本主体与意图词。 */
  private String stripOtherSubjects(String query, String keep) {
    String result = query;
    for (SubjectEntry entry : dictionary) {
      if (entry.name().equals(keep)) {
        continue;
      }
      result = result.replace(entry.name(), " ");
      if (entry.code() != null && !entry.code().isBlank()) {
        result = result.replace(entry.code(), " ");
      }
      for (String alias : entry.aliases()) {
        result = result.replaceAll("(?i)" + java.util.regex.Pattern.quote(alias), " ");
      }
    }
    return result.replaceAll("\\s+", " ").trim();
  }

  /** 文档主体归属：source 文件名含规范名/别名/代码（大小写不敏感），词典序先到先得。 */
  private String attribute(Document doc) {
    Object source = doc.getMetadata().get("source");
    if (source == null) {
      return null;
    }
    String file = String.valueOf(source).toLowerCase(Locale.ROOT);
    for (SubjectEntry entry : dictionary) {
      if (file.contains(entry.name().toLowerCase(Locale.ROOT))
          || (entry.code() != null && !entry.code().isBlank() && file.contains(entry.code()))
          || entry.aliases().stream().anyMatch(a -> file.contains(a.toLowerCase(Locale.ROOT)))) {
        return entry.name();
      }
    }
    return null;
  }
}
