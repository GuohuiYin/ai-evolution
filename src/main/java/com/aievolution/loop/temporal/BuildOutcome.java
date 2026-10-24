package com.aievolution.loop.temporal;

import com.aievolution.loop.LoopStep;
import java.util.List;

/**
 * Builder 一轮产出的 payload（W22 #1c）：取证轨迹 + 草稿终答。 {@code draft=null} 是合成失败哨兵（诚实红线：answer=null 收尾，
 * 不送复核——无草稿可审）。
 */
public record BuildOutcome(List<LoopStep> steps, String draft) {}
