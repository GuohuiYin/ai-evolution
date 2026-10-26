# W23 #3b L4 全项验收：三角色管线 + Skill + HITL 三项合体走查（2026-10-26）

> 走查口径：三机制同开（`AI_LOOP_ENGINE=pipeline` + `AI_SKILL_MODE=progressive` +
> `AI_LOOP_HITL_ENABLED=true`），真机（Temporal dev server）跑一案，验证合体无干扰、
> 各机制行为边界正确。

## 走查案

「茅台 2024 年度现金分红占归母净利润的比例是多少」（分红题，Agent 通路）

## 三机制轨迹实录

| 机制 | 观察点 | 实录 |
|---|---|---|
| 三角色管线 | plan→build→review 串行 | `plan ready subQuestions=1` → Builder 子循环 4 步取证（财务工具+公告检索）→ Reviewer 通过 → `exit stopReason=FINAL_ANSWER` |
| Skill（progressive） | 按需注入、命中恰当 | 每研究步触发 `[annual-report-crossread, dividend-caliber, tool-handbook]`——分红题命中 dividend-caliber，触发域正确 |
| HITL | 复核通过**不挂起**（裁决①B 边界） | 全程 0 条 hitl 事件，`pendingApproval` 未置位——通过路径零干扰 |

终答：「40.21%（年度利润分配预案口径）」，来源/时点标注完整。

## 合体结论

三机制正交无干扰：管线管编排、Skill 管提示词按需注入、HITL 管打回耗尽的人工兜底——
各守一段，同开不打架。唯一交集点（HITL 挂起）已在 #1 双路径演示中单独验收
（[w23-hitl-demo](w23-hitl-demo.md)）。

## L4 全项对照（蓝图 W21-23「L4 完整」行）

| 项 | 状态 | 证据 |
|---|---|---|
| 三角色子代理管线 | ✅ 默认引擎 | [w22-pipeline-dual-arm](w22-pipeline-dual-arm.md)（57 案 +0.33、红线 14/14） |
| Skill 渐进披露 | ✅ 机制在库、实测登记止步 | [w23-skill-token-report-r2](w23-skill-token-report-r2.md)（降幅 9.7% <20% 守线） |
| HITL 审批断点 | ✅ 打回耗尽触发接通 | [w23-hitl-demo](w23-hitl-demo.md)（双路径真机演示） |
| A2A 协议认知 | ✅「会用」档 | [a2a-protocol](../notes/a2a-protocol.md)（含 MCP 边界对照） |
