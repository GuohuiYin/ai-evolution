package com.aievolution.loop.temporal;

/**
 * HITL 模式（W31 #2，Owner 裁决 A）：打回耗尽升级人工时的两种运行模式。
 *
 * <ul>
 *   <li>{@link #ATTENDED} 值守模式（默认，W23 起既有行为）：挂起等人工 Signal，超时未决 fail-closed 拒答
 *   <li>{@link #UNATTENDED} 无人模式：不挂起不空等，立即 fail-closed 拒答——W26 实账：无人窗口挂起 100% 空转 10
 *       分钟后同样拒答，空等纯损；红线零让步（复核未过的草稿任何模式都不放出）
 * </ul>
 */
public enum HitlMode {
  ATTENDED,
  UNATTENDED;

  /** 配置值解析（attended/unattended，大小写不敏感）；非法值 fail-fast 不静默落默认 */
  public static HitlMode parse(String value) {
    if (value == null || value.isBlank()) {
      return ATTENDED;
    }
    return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
      case "attended" -> ATTENDED;
      case "unattended" -> UNATTENDED;
      default ->
          throw new IllegalArgumentException(
              "ai.loop.hitl.mode 只接受 attended/unattended，收到: " + value);
    };
  }
}
