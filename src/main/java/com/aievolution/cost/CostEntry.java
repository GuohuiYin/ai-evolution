package com.aievolution.cost;

import java.time.Instant;

/**
 * 一次模型调用的计量条目（W25 #2a）：token 是一手真相（取自响应元数据，含供应商原生缓存命中）， 计价是查询时可变映射——计量与计价分离，调价不改历史台账。
 *
 * @param channel 通路标记（rag / agent / analysis 等），由调用方经 advisor 上下文注入
 * @param cacheReadTokens 缓存命中量，供应商未上报为 null（记 null 不记 0——缺失与零命中是两回事）
 */
public record CostEntry(
    Instant at,
    String model,
    String channel,
    long promptTokens,
    long completionTokens,
    Long cacheReadTokens) {}
