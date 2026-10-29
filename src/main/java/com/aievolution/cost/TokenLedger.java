package com.aievolution.cost;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * token 台账（W25 #2a，ADR 待补）：每次模型调用追加一行 JSONL，文件持久化——重启不丢是 预算熔断（fail-closed）成立的前提，纯内存台账会让重启成为逃债通道。
 *
 * <p>选型理由（Owner 2026-10-29 拍板 JSONL）：本地每天几十次调用的量级，读时聚合无压力； 零新增基础设施、可 diff
 * 可审计。多副本部署时须收口共享存储（重评触发条件）。
 *
 * <p>纪律：写失败只告警不抛（观测永不击穿业务，{@code TraceSpans} 同款约定）； 读失败抛 {@link
 * UncheckedIOException}——读是熔断的输入，fail-closed 由调用方裁决（#2b）。
 */
@Component
public class TokenLedger {

  private static final Logger log = LoggerFactory.getLogger(TokenLedger.class);

  /** 单日聚合视图：总量 + 按模型/按通路两个维度（仪表盘端点 #2a-ii 的直接素材） */
  public record DailySummary(
      LocalDate day,
      int calls,
      long promptTokens,
      long completionTokens,
      long cacheReadTokens,
      Map<String, long[]> byModel,
      Map<String, long[]> byChannel) {}

  private final Path ledgerPath;
  private final Clock clock;
  private final ObjectMapper mapper;

  @Autowired
  public TokenLedger(@Value("${ai.cost.ledger-path:data/cost-ledger.jsonl}") String ledgerPath) {
    this(Path.of(ledgerPath), Clock.systemDefaultZone());
  }

  /** 测试友好构造：目录与时钟皆可注入 */
  public TokenLedger(Path ledgerPath, Clock clock) {
    this.ledgerPath = ledgerPath;
    this.clock = clock;
    this.mapper =
        new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  /** 追加一条计量（synchronized：单写者进程内串行即可，不上文件锁） */
  public synchronized void record(
      String model, String channel, long prompt, long completion, Long cacheRead) {
    CostEntry entry =
        new CostEntry(Instant.now(clock), model, channel, prompt, completion, cacheRead);
    try {
      Files.createDirectories(ledgerPath.getParent());
      Files.writeString(
          ledgerPath,
          mapper.writeValueAsString(entry) + "\n",
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    } catch (IOException e) {
      log.warn("token 台账写入失败（业务不受影响，本条计量丢失）: {}", e.getMessage());
    }
  }

  /** 按日聚合（本地时区界天）；台账不存在视为空账（首日运行合法），读故障抛 UncheckedIOException */
  public DailySummary summarize(LocalDate day) {
    List<CostEntry> entries = readAll();
    int calls = 0;
    long prompt = 0, completion = 0, cache = 0;
    Map<String, long[]> byModel = new LinkedHashMap<>();
    Map<String, long[]> byChannel = new LinkedHashMap<>();
    ZoneId zone = clock.getZone();
    for (CostEntry e : entries) {
      if (!LocalDate.ofInstant(e.at(), zone).equals(day)) {
        continue;
      }
      calls++;
      prompt += e.promptTokens();
      completion += e.completionTokens();
      cache += e.cacheReadTokens() == null ? 0 : e.cacheReadTokens();
      accumulate(byModel, e.model(), e.promptTokens() + e.completionTokens());
      accumulate(byChannel, e.channel(), e.promptTokens() + e.completionTokens());
    }
    return new DailySummary(day, calls, prompt, completion, cache, byModel, byChannel);
  }

  /** 今日汇总（端点 #2a-ii 的直接素材）：按台账时钟取「今天」 */
  public DailySummary summarizeToday() {
    return summarize(LocalDate.now(clock));
  }

  /** 维度桶累加：[0]=total tokens，[1]=调用次数 */
  private static void accumulate(Map<String, long[]> bucket, String key, long tokens) {
    long[] cell = bucket.computeIfAbsent(key, k -> new long[2]);
    cell[0] += tokens;
    cell[1]++;
  }

  private List<CostEntry> readAll() {
    if (!Files.exists(ledgerPath)) {
      return List.of();
    }
    try {
      List<CostEntry> entries = new ArrayList<>();
      for (String line : Files.readAllLines(ledgerPath)) {
        if (line.isBlank()) {
          continue;
        }
        try {
          entries.add(mapper.readValue(line, CostEntry.class));
        } catch (IOException e) {
          // 单行损坏不毁整天：跳过留痕（残缺行多为上次进程被杀的半行写）
          log.warn("token 台账跳过损坏行: {}", line.substring(0, Math.min(60, line.length())));
        }
      }
      return entries;
    } catch (IOException e) {
      throw new UncheckedIOException("token 台账读取失败: " + ledgerPath, e);
    }
  }
}
