# W28 任务1 验收报告：改写注入标识符护栏（RewriteIdentifierGuard）

> 2026-11-05 跑批结案。状态：**双门达成，待 Owner review 提交**。
> 设计草案：[w28-recall-enhancement-design](w28-recall-enhancement-design.md)（四候选取证 + 机制链）。
> 实现：`RewriteIdentifierGuard`（新）+ `DeepSeekQueryRewriter` 接线 + `query-rewrite-v1.md`
> 软约束（不增补标识符规则）+ 开关 `ai.rag.rewrite.strip-injected-identifiers`（默认开）。

## 一、验收双门（w28 计划退出条件）

| 门 | 结果 | 证据 |
|---|---|---|
| 门 1：案 30 双跑转绿 | ✅ **满分 6/6**（judge：引用 2026 半年报、陈华、最新披露口径一致、溯源完整）；此前单独实测亦转绿（2025 年报口径 + 变更沿革并注） | 本轮跑批日志；2026-11-04 18:02（关臂，仅及控股股东层面）vs 11-05 开启臂对照 |
| 门 2：检索 Recall@5 不回归 + 生成 eval 不降 | ✅ 检索双臂 52/53 vs 52/53（98.1%），负例拒答 8/16 vs 8/16 **零回归**；生成 eval 总体 **5.8/6 vs 基线 5.6 不降反升**，各类别见 §二 | 检索双臂日志；本文 §二 |

## 二、生成 eval 双臂对照（58 案，开启臂全量 2026-11-05 11:19 收卷）

| 类别 | W26 v3.3 基线 | 本轮（开启臂） | 判读 |
|---|---|---|---|
| factual | 6.0（20 案） | 6.0（18 案） | 持平 |
| redline | 5.9 | 6.0（6 案） | 持平偏升 |
| compound | 5.1 | 5.5（10 案） | 升 |
| multi-turn | 5.0 →（W27 修复后 5.7） | 5.6（8 案） | 持平（-0.1 在判分噪声带内） |
| in-domain-unanswerable | 6.0（7 案） | 5.8（6 案） | 单案 5 分所致，见 §三 |
| **总体** | **5.6** | **5.8**（48 案） | **不降** |

失败案 10 条（不进均分）：**全部 WorkflowFailedException**（planner 120s 活动预算超时，
即挂账 #2 failover 超时联动，W31 韧性捆处置）——零 BudgetExceeded（提额后跑全程）。

## 三、边界案诚实登记

1. **「为什么不能继续扩」3 分**：与 W26 基线同分（3），多轮上下文缺口族既有缺陷，
   非本轮回归（W26 报告 §四已归因）。
2. **「宁德时代固态电池什么时候量产」5 分（accuracy 1）**：judge 判「拒答合规但遗漏
   2026 半年报固态电池在研披露」——**新发现的召回漏检案**（查询无标识符，与护栏无关）；
   属案 30 同族（召回窄带漏检）的另一表征，建议走 badcase 裁定台登记为黄金集候选。
3. **护栏触发面**：检索 eval 全程仅触发 1 次（董事长案：剥离改写增补的「2026/5」），
   未影响该案通过；生成 eval 全程触发 0 次——干预面符合「最小手术」设计预期。

## 四、rerank 重评触发核查（任务 1⑤）

触发条件（w13-rerank-eval）：召回侧格局变化 / 黄金集扩入排名类用例 / 线上排错位实证。
本轮护栏为查询侧外科手术，黄金集双臂召回读数**逐案一致**（52/53、8/16 全同）——
召回格局未变，触发条件不成立，rerank 维持默认关。

## 五、跑批成本与环境留痕

- 当日 token 用量 1,678,984 / 提额后预算 2,500,000（Owner 10:52 批准；默认 20 万，
  当日调整次日自动回落，留痕 `data/budget-override.json`）
- 插曲：首跑 20 万默认预算熔断（6/58 完成）、二跑 80 万仍熔断（36/58 完成）——
  两轮作废数据未作任何裁决依据；pipeline 通路单案 token 消耗是预算规划参考
- 复现：`AI_EVAL_GENERATION_ENABLED=true ./mvnw spring-boot:run`（开启臂 = 默认配置）；
  关臂对照 `AI_RAG_REWRITE_STRIP_INJECTED_IDENTIFIERS=false`

## 六、提交清单（A2，待 Owner 确认）

| 文件 | 变更 |
|---|---|
| `src/main/java/com/aievolution/rag/RewriteIdentifierGuard.java` | 新增（~75 行）：增补标识符剥离，白名单 = 原查询 + 对话历史 |
| `src/main/java/com/aievolution/rag/DeepSeekQueryRewriter.java` | 接线 + 关断开关 + 剥离留痕（stage=REWRITE_GUARD），+~35 行 |
| `src/test/java/com/aievolution/rag/RewriteIdentifierGuardTest.java` | 新增 9 项单测 |
| `src/test/java/com/aievolution/rag/QueryRewriterTest.java` | +5 项接线测试（构造签名同步） |
| `src/main/resources/prompts/query-rewrite-v1.md` | 主体清单去代码示范 + 不增补标识符规则（A13 门证据即本报告 §一/§二） |
| `src/main/resources/application.yml` | 开关配置项 + 注释 |
| `docs/eval/retrieval/w28-recall-enhancement-design.md` | 设计草案（任务 1① 产出） |
| `docs/eval/retrieval/w28-sparse-probe.json` | 生产链 sparse 裁决探针证据 |
| `docs/scripts/w28-recall-probe.py` | 四候选取证探针 |
| 本文档 | 验收报告 |

测试状态：`./mvnw verify` 全绿（含 Testcontainers IT）；rag 包 134 测试绿；
spotless 通过。
