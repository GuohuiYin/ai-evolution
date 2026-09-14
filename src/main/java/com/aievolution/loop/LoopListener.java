package com.aievolution.loop;

/** Loop 轨迹观测端口（W11 #2）：每步完成与循环退出时的回调点。 SSE 轨迹推送（#3 接线）、轨迹评估指标采集（W13 验收门）都挂在这个端口上， 不入侵循环本体（开闭原则）。 */
public interface LoopListener {

  /** 不观测：三参构造的缺省值 */
  LoopListener NONE = new LoopListener() {};

  /** 一步执行完成（action → observation 已留痕） */
  default void onStep(LoopStep step) {}

  /** 循环退出（含收敛与达上限两种情况，以 {@link LoopResult#stopReason()} 区分） */
  default void onComplete(LoopResult result) {}
}
