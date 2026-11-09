# W31 值守窗口运行报告（failover 断流 + HITL 无人模式 + 安全审计流）

> 窗口时间：2026-11-08 15:42 – 18:20（Asia/Shanghai）
> 目的：W31 任务 #1（failover 超时联动）、#2（HITL 无人模式）、#4（安全审计流）的实测验收
> 配置：`AI_LOOP_ENGINE=pipeline AI_LOOP_HITL_MODE=unattended`，hosts 断流注入 `127.0.0.1 api.deepseek.com`（15:46 Owner 注入）
> 执行单：`docs/ops/w31-acceptance-runbook.md`（f5a71d4）

## 一、结论先行

**窗口目标全部达成**，过程中实测抓获三个真实故障（F1/F2/F3），其中 F1/F2 当场修复并复验通过（`5378b27`），F3 修复后未复现、留观察。

| 验收项 | 结果 |
|---|---|
| #1 failover 超时联动（断流换臂不 500） | ✅ 修复后 259s 200 出正确答案 |
| #2 HITL 无人模式（打回耗尽秒级拒答） | ✅ 耗尽后 10ms 拒答，`unattended-refusal` 事件在案 |
| #4 安全审计流（401/403/429 对照） | ✅ 三类齐全带 traceId，独立 `logs/security-audit.log` 分流 |
| 意外收获：预算闸实账熔断 | ✅ 202,231/200,000 撞线熔断，提额后恢复 |

## 二、三项故障发现（窗口核心价值）

### F1：运行中途断流 → 整单 500

- **实账**：15:48 第一笔（traceId=860586c5）896s 后 500，`TimeoutFailure: activity StartToClose timeout`（Review 活动）。workflow 启动时臂=主力（首个 failover 在启动后 5 秒才点火），运行中换臂不换档，活动按主力档 2 分钟预算撞墙。
- **修复**（`5378b27`）：`TemporalPipelineEngine` 捕获 `WorkflowFailedException`，启动臂=主力且失败时臂=备用（=运行期间发生了 failover）则以同 workflowId 重跑一次，预算按备用档重取。臂未变的确定性故障原样抛出，不浪费重试。
- **生产点火证据**：17:58:46 日志 `运行期间发生 failover，同 workflowId 重跑一次 workflowId=pipeline-...`（17:41 那笔原跑在重建环节失败后由引擎自动重跑，最终走到打回耗尽拒答）。

### F2：冷却窗内每次调用先试死主力，重试风暴 40+ 次/调用

- **实账**：failover 点火后，后续每次模型调用仍先试主力，Spring AI 重试模板对连接拒绝重试 40+ 次、每次约 2s，每次调用白烧 ~90s。
- **修复**（`5378b27`，**裁决修订 A→B**：原 W31 #1 裁决「只登记臂位不改调用路径」，窗口实测后 Owner 批准修订为「冷却窗内直连备用臂」）：`FailoverChatModel` 的 call/stream 在冷却窗内直连备用臂；窗口过后恢复先试主力（一次自然探测：通则回主力，断则重进冷却窗）。冷却判定收口为 `inCooldown()` 单点。
- **复验对比**：修复后 16:46 那笔全程 Spring AI 重试仅 **2 次**（首次自然探测），对比修复前每次调用 40+ 次。

### F3：Qwen 长调用在 ~243s 被客户端取消

- **实账**：run 2（traceId=fd6158af）两次 Build 调用都精确 ~243s 被取消（`OpenAIIoException: Canceled`，16:08:41→16:12:44、16:15:02→16:19:05）。PT6M HTTP 读超时定制器没拦住。
- **现状**：F1/F2 修复后的复验（16:46 那笔，259s 完整跑穿）与 HITL 验证（多轮 Qwen 长调用）**均未复现**。取消源未定位（可疑：openai-java 客户端内部机制 / SiliconFlow 网关），留观察——若复现，单独开任务定位。

## 三、复验时间线（修复后）

| 时刻 | 事件 |
|---|---|
| 16:45 | 新代码重启完成（断流仍注入） |
| 16:46:39 | failover 点火（首次调用自然探测，~2s），traceId=c952b211 |
| 16:50:55 | **259s 拿到 HTTP 200**：茅台 2024 营收 170,899,152,276.34 元（约 1,709 亿），来源标注+免责声明齐全，Reviewer PASS |
| 16:51:45 | 追问（"它 2024 年的净利润是多少？"）：冷却窗刚过，探测主力→失败→重进冷却窗（仅 ~2s 损耗），追问经会话上下文正确承接主体后正常作答——HITL 未自然触发（符合执行单预判，转备选注入路径） |
| 17:21 | **预算闸熔断**：「今日 token 预算已用尽（已用 202231 / 预算 200000）」——成本红线按设计生效；`AI_COST_DAILY_TOKEN_BUDGET=600000` 提额重启恢复 |

## 四、HITL 无人模式验证（T+45，走备选注入路径）

自然触发未发生后，按执行单备选方案：临时 `reviewer-always-reject.md`（不入库，窗口结束已删），`AI_LOOP_REVIEWER_PROMPT=reviewer-always-reject` 重启，一笔触发打回耗尽。

- 打回序列：attempt=0（18:03:19）→ attempt=1（18:05:42）→ attempt=2（18:13:11，耗尽）
- **18:13:11.081 打回耗尽 → 18:13:11.091 `hitl event=unattended-refusal`（+10ms，秒级）→ fail-closed 拒答 `exit stopReason=REVIEW_REJECTED`**
- 不再空转挂起 10 分钟——#2 核心断言达成

## 五、安全审计流三连（T+55）

| 注入 | 实测 | 审计留痕（`logs/security-audit.log`） |
|---|---|---|
| 无凭证 POST /ai/agent | 401 | 18:16:42 `event=AUTH_FAILURE` traceId=2e413208 |
| 机器令牌（reader）POST /ai/cost/budget | 403 | 18:16:42 `event=AUTHZ_DENIED` traceId=16cc61eb subject=mcp-client |
| 75 并发 POST /ai/chat | 前 60 放行、第 61 起 429（阈值 60/分钟，含前一波 12 笔计数） | 15 条 `event=RATE_LIMITED` 均带 traceId，identity=sub:mcp-client |

三类齐全、带 traceId、与业务日志分流——#4 核心断言达成。（另有 REVIEW_REJECTED 打回审计 7 条同日志在案。）

## 六、执行单证据表回填

| 注入 | 断言 | 实测 | 证据 | 判定 |
|---|---|---|---|---|
| ①断流换臂 | pipeline 请求 200 零 500 | 259s 200，答案正确 | traceId=c952b211，§三 | ✅ |
| ①断流换臂 | 预算档换备用档 | 修复后 Qwen 259s 长调用跑穿未爆；F1 重跑按备用档取预算（单测锁定 360s/1200s 档） | `TemporalPipelineEngineTest` + §二 F1 | ✅ |
| ②无人模式 | 打回耗尽秒级拒答（<60s） | 10ms | 18:13:11 日志序列，§四 | ✅ |
| ②无人模式 | hitlEvent 含 unattended-refusal | 命中 | `hitl event=unattended-refusal` | ✅ |
| ③审计流 | 三类齐全 | AUTH_FAILURE/AUTHZ_DENIED/RATE_LIMITED 齐 | §五 | ✅ |
| ③审计流 | 带 traceId、与业务日志分流 | 齐 | `logs/security-audit.log` | ✅ |

## 七、遗留与观察项

1. **F3 取消源未定位**：修复后未复现，留观察；复现则开任务定位（可疑方向：openai-java `PhantomReachableClosingHttpClient` / SiliconFlow 网关空闲断开）。
2. **日预算 200k 对 Qwen thinking 偏紧**：备用臂复核的 reasoningContent 是消耗大头（本窗口约 10 次 Qwen 调用烧穿 200k）。 failover 期预算与臂联动（预算档已按臂分，token 预算是否也要按臂分档）建议纳入 W32 讨论。
3. **hosts 恢复**：`sudo sed -i '' '/^127\.0\.0\.1 api\.deepseek\.com$/d' /etc/hosts`（窗口结束由 Owner 执行）。
4. 窗口期间 Temporal 耐久性附带验证：重启后 RUNNING 工作流自动续跑（追问工作流 17:06 续跑完成）；终态（FAILED）不重跑，符合语义。

## 八、窗口产出提交

| commit | 内容 |
|---|---|
| `5378b27` | fix(loop): 冷却窗直连备用臂 + 运行期 failover 引擎级重跑（F1+F2，含 +7 测试） |
| `3aa248c` | fix(scripts): auth_token.py 补主入口 |
