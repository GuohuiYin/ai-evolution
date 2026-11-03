package com.aievolution.cost;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 日预算运行时调整（W27 #4，W26 报告 P5/S1）：撞线后值班经 {@code POST /ai/cost/budget} 当日调额， 不再靠改环境变量重启——W26 两次撞线各约 1
 * 分钟的流量损失即重启所致。
 *
 * <p>生效范围（Owner 2026-11-03 拍板）：<b>当日有效，次日自动回落</b>配置默认——防「临时救场提额 变永久超支口子」。调整值落盘
 * JSON（重启回放，当日仍有效），与台账同目录同风格。
 *
 * <p>读路径零 IO：{@link #effectiveBudget()} 在每次模型调用前被预算闸读一次，是 volatile 内存读； 文件只在启动回放与调整落盘时碰。override
 * 文件缺失/损坏/过期一律回落配置默认（从严方向）并留痕。
 */
@Component
public class DailyBudgetOverride {

  /** 与预算闸共用留痕 logger（ai.cost.budget）：撞线与调额在同一订阅面 */
  private static final Logger budgetLog = LoggerFactory.getLogger("ai.cost.budget");

  /** 一次调整的完整留痕：生效日 + 额度 + 理由 + 调整时点；即落盘 JSON 的格式 */
  public record Adjustment(LocalDate day, long budget, String reason, Instant adjustedAt) {}

  private final long defaultBudget;
  private final Path overridePath;
  private final Clock clock;
  private final ObjectMapper mapper;
  private volatile Adjustment current;

  @Autowired
  public DailyBudgetOverride(
      @Value("${ai.cost.daily-token-budget:200000}") long defaultBudget,
      @Value("${ai.cost.budget-override-path:data/budget-override.json}") String overridePath) {
    this(defaultBudget, Path.of(overridePath), Clock.systemDefaultZone());
  }

  /** 测试友好构造：默认值、路径、时钟皆可注入 */
  public DailyBudgetOverride(long defaultBudget, Path overridePath, Clock clock) {
    this.defaultBudget = defaultBudget;
    this.overridePath = overridePath;
    this.clock = clock;
    this.mapper =
        new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    this.current = replay();
  }

  /** 当日生效预算：有当日调整值用之，否则配置默认。次日自动回落由此处的日期比较保证 */
  public long effectiveBudget() {
    Adjustment a = current;
    return a != null && a.day().equals(today()) ? a.budget() : defaultBudget;
  }

  /** 当前调整值快照（仪表盘端点素材）：无或已过期即 null */
  public Adjustment currentAdjustment() {
    Adjustment a = current;
    return a != null && a.day().equals(today()) ? a : null;
  }

  public long configuredDefault() {
    return defaultBudget;
  }

  /** 当日调额：内存先生效、落盘留痕随后；写失败降级为仅内存生效（重启后回落默认）并告警 */
  public synchronized void adjust(long budget, String reason) {
    long before = effectiveBudget();
    Adjustment next =
        new Adjustment(today(), budget, reason == null ? "" : reason, Instant.now(clock));
    current = next;
    try {
      Files.createDirectories(overridePath.getParent());
      Files.writeString(overridePath, mapper.writeValueAsString(next) + "\n");
    } catch (IOException e) {
      budgetLog.warn("预算调整落盘失败（仅内存生效，重启后回落默认）: {}", e.getMessage());
    }
    budgetLog.warn(
        "日预算运行时调整: {} -> {}（{} 当日有效）reason={}", before, budget, next.day(), next.reason());
  }

  /** 启动回放：文件缺失/损坏/过期都回落默认（从严方向），不阻断启动 */
  private Adjustment replay() {
    if (!Files.exists(overridePath)) {
      return null;
    }
    try {
      Adjustment a = mapper.readValue(Files.readString(overridePath).trim(), Adjustment.class);
      if (!a.day().equals(today())) {
        budgetLog.info("预算调整值已过期（{}），回落配置默认 {}", a.day(), defaultBudget);
        return null;
      }
      budgetLog.warn("预算调整值重启回放: {}（{} 当日有效）reason={}", a.budget(), a.day(), a.reason());
      return a;
    } catch (IOException | RuntimeException e) {
      budgetLog.warn("预算调整值文件损坏，回落配置默认: {}", e.getMessage());
      return null;
    }
  }

  private LocalDate today() {
    return LocalDate.now(clock);
  }
}
