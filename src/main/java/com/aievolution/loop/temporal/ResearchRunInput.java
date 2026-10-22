package com.aievolution.loop.temporal;

import java.util.List;

/**
 * 一轮研究运行的 workflow 入参（W21 #1②）。
 *
 * @param question 用户问题
 * @param maxSteps 步数上限（引擎装配侧已校验 ≥1，同 ResearchLoop 构造语义）
 * @param history 会话历史（压平载体）
 * @param traceId 全链路追踪 ID——activity 侧重建 MDC，研究轨迹日志不断链
 */
public record ResearchRunInput(
    String question, int maxSteps, List<HistoryTurn> history, String traceId) {}
