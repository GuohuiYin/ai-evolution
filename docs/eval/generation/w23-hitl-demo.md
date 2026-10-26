# W23 #1 HITL 审批断点真机验收（2026-10-26）

> 裁决①方案 B 范围：唯一接通触发=「打回耗尽升级人工」；红线擦边/低置信证据两条策略留埋点。
> 真机环境：Temporal dev server（UI :8088）+ pipeline 引擎 + `AI_LOOP_HITL_ENABLED=true`。

## 机制

- 触发：Reviewer 复核 3 轮（首轮 + 打回 2 次）仍不过 → 不直接拒答，workflow 挂起
  （`status=awaiting-approval`，`pendingApproval=true`）
- 人工决定入口：`POST /api/approvals/{workflowId}`（decision=approve/reject + comment）；
  状态查询 `GET /api/approvals/{workflowId}`
- 缺席策略显式化：`ai.loop.hitl.await-timeout`（默认 PT2H）内未决 → fail-closed 拒答
  （宁可不答不可错答）
- 留痕：hold/approve/reject/timeout 四事件三处可见——research-trace 日志（含等待耗时）、
  SSE 轨迹（合成一步 tool=human-approval 实时推送，零 API 变更）、Temporal 事件史
  （WorkflowExecutionSignaled）
- 放行语义：放出最后草稿并**如实标注**「自动复核未通过，经人工审批放行，内容由审批人背书」

## 双路径真机演示记录

演示杠杆：本地临时 reviewer prompt（恒打回，稳定触发耗尽路径；演示后已删除，不入库）。

| 路径 | 过程 | 证据 |
|---|---|---|
| 挂起→放行→续跑 | 问「贵州茅台 2024 年营收」→ 3 轮打回 → 挂起 136.7s → POST approve → 续跑收尾 | SSE 收到 hold 事件 + 终答含人工背书标注；日志 `hitl approved comment=演示放行… waited=136722ms`；Temporal 事件史含 WorkflowExecutionSignaled |
| 挂起→驳回→终止 | 问「宁德时代 2024 年归母净利润」→ 3 轮打回 → 挂起 139.3s → POST reject → 拒答文案收尾 | SSE delta=「经多轮独立复核仍无法满足溯源与合规要求，本次不予作答…」；日志 `hitl rejected comment=演示驳回… waited=139303ms` |

## 测试

- workflow 层 +4 案：放行放草稿（含背书标注）/ 驳回维持拒答 / 超时 fail-closed
  （TestWorkflowEnvironment 跳时钟）/ 复核通过永不挂起（裁决①B 边界）
- 控制器层 +6 案：状态查询 / 放行 / 驳回 / 非挂起 409 / 未知 workflow 404 / 非法决定 400
- 全量 366 测试绿

## 配置

```yaml
ai.loop.hitl.enabled: ${AI_LOOP_HITL_ENABLED:false}        # 默认关，验收后由 Owner 定夺翻默认
ai.loop.hitl.await-timeout: ${AI_LOOP_HITL_AWAIT_TIMEOUT:PT2H}
```

默认保持 false（即时拒答旧语义）——是否翻 true 留 Owner 裁决（HITL 挂起意味着会话长时间
占用，产品形态需先定：当前实现为同步等待，适合演示与低频高审场景）。
