package com.aievolution.loop;

/**
 * 研究循环引擎端口（W21 #1② 路线 C）：inline（{@link ResearchLoop} 进程内直跑）与 temporal（loop.temporal 包，事件历史
 * checkpoint + 断点恢复）双引擎的统一调用面， 经 {@code ai.loop.engine} 旗标切换（Tushare {@code ai.stock.data-source}
 * 同款配置开关先例）。
 */
public interface ResearchLoopEngine {

  /** 跑一轮研究循环：串行"模型决定 → 调工具 → 再决定"，收敛或触顶退出 */
  LoopResult run(String question);
}
