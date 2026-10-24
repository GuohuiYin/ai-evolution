package com.aievolution.loop;

/** Loop 退出原因 */
public enum StopReason {
  /** 模型给出终答，正常收敛 */
  FINAL_ANSWER,
  /** 达步数上限，优雅退出（防死循环/注入诱导反复调工具烧 token） */
  MAX_STEPS,
  /** Reviewer 打回耗尽仍不过，拒答收尾（宁可不答不可错答，W22 三角色管线） */
  REVIEW_REJECTED
}
