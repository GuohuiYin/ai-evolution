package com.aievolution.loop;

/** 研究 Loop 的一步留痕（W11 #1）：thought/action/observation 三元组，轨迹可观测（#2）与轨迹评估的数据基。 */
public record LoopStep(int index, String thought, String tool, String input, String observation) {}
