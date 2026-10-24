package com.aievolution.loop.pipeline;

import com.aievolution.loop.ToolExecutor;
import com.aievolution.loop.ToolRegistry;
import java.util.List;
import java.util.Set;

/**
 * Builder 的通路白名单执行器（W22 #1c）：子问题被 Planner 标注唯一通路后， 该子问题的取证既收窄工具手册（prompt 层，{@link #filteredSpecs}），
 * 也在执行层拦越权调用——跨通路调用降级为错误观察喂回模型自我纠正， 不抛异常击穿循环（ToolRegistry 同款失败哲学）。
 *
 * <p>通路 → 工具映射是当前工具面板的唯一事实源：rag/announcement 均落到 {@code searchAnnouncements}（语义检索语料含定期报告与公告）。
 */
public class ChannelToolExecutor implements ToolExecutor {

  private final ToolExecutor delegate;
  private final Channel channel;
  private final Set<String> allowed;

  public ChannelToolExecutor(ToolExecutor delegate, Channel channel) {
    this.delegate = delegate;
    this.channel = channel;
    this.allowed = toolsFor(channel);
  }

  /** 通路白名单（与 planner-v1 prompt 的通路定义对应） */
  public static Set<String> toolsFor(Channel channel) {
    return switch (channel) {
      case RAG, ANNOUNCEMENT -> Set.of("searchAnnouncements");
      case STOCK -> Set.of("getDailyQuotes", "getFinancialSummary");
      case NONE -> Set.of();
    };
  }

  /** 过滤工具手册：渲染进 Builder 系统 prompt 的面板与执行白名单同源（A11） */
  public static List<ToolRegistry.ToolSpec> filteredSpecs(
      List<ToolRegistry.ToolSpec> specs, Channel channel) {
    Set<String> allowed = toolsFor(channel);
    return specs.stream().filter(s -> allowed.contains(s.name())).toList();
  }

  @Override
  public String execute(String tool, String input) {
    if (!this.allowed.contains(tool)) {
      return "工具 %s 不可用：本子问题限定通路为 %s（可用工具：%s）"
          .formatted(tool, this.channel.toJson(), String.join(" / ", this.allowed));
    }
    return this.delegate.execute(tool, input);
  }
}
