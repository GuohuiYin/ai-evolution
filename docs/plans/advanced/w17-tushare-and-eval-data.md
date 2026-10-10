# W17 计划：行情/财务真源替换（Tushare pro）+ 专项二·评估造数启动

> 蓝图 W17-18 行（专项二·评估造数）首周 + 能力地图唯一主代码 mock 挂账收口。
> 本周双线：#1 收口挂账（半天~一天级），#2 启动双周专项的第一刀（造数规范先行）。

## 背景（为什么做）

1. **主代码唯一 mock 点挂账已久**（[capability-map](../../capability-map.md) Agent 核心·工具行）：
   个股分析主链路数据仍来自 `MockStockDataClient`（W4 mock 先行，W5 真源未换、台账漏记，
   2026-09-29 扫描重新发现，`ecc7472` 登记）。mock 种子只有 2 股 × 2 交易日 + 2024 财务，
   任何真实问股（"茅台昨天收盘价"）都只能空收或答假窗口——演示 PPT 前必须收口。
   切换面 `ai.stock.data-source` 在 W4 已预留（`@ConditionalOnProperty` mock 缺省），
   调用方（StockDataTools / StockAnalysisService / ExternalToolClient）全部面向
   `StockDataClient` 接口，换源零调用方改动（开闭原则已就位）。
2. **专项二·评估造数到归期**（蓝图 W17-18 行）：检索黄金集 39 条 + 生成 22 条，
   W13/W15/W16 全部裁决都压在这 61 条上——样本量不足是评估链路的系统性风险
   （W16 #1 交叠带结论、#2 负例守线结论的统计功效都受样本量约束）。
   双周专项目标：黄金集 100+、Judge 与人工抽检一致率 ≥80%、防泄漏机制。
   本周只启动第一刀：造数规范与扩容起步，不追求一周达标。

## 任务分解表

| # | 任务 | 验收条件 | 状态 |
|---|---|---|---|
| 1 | **TushareStockDataClient 真源实现（TDD）**：`StockDataClient` 新增实现，`ai.stock.data-source=tushare` 装配（mock 留缺省）；日行情（daily 接口）+ 年度财务（income 或等价接口）字段映射到 `DailyQuote`/`FinancialSummary`（source 诚实标 "tushare"，asOf 真实日期）；token 经 `ai.stock.tushare.token` 配置（env `TUSHARE_TOKEN`，.env.example 登记）；超时/限流/未知代码语义与接口契约对齐（空列表/Optional.empty，不抛异常）；故障行为显式（任务内裁决：报错 or 降级 mock——若降级必须 source 标注可溯源，红线 03）；单测 mock HTTP 层 + 与 Mock 同断言集的契约测试 | `data-source=tushare` 下个股分析链路返回真源数据且 source/asOf 可溯源；mock 缺省不变；clean verify 绿 | ✅ `4e75ec3`（REST 直连；代码后缀/日期/元→亿元映射；故障显式报错；JDK HttpServer 契约测试 6 条——MockRestServiceServer 被 requestFactory 覆盖的坑入注释） |
| 2 | **真源冒烟 IT（打标隔离）**：真实 Tushare 调通 600519 行情+财务各一条，`TUSHARE_TOKEN` 缺失自动 skip（JUnit `Assumptions` 或 tag），不拖 CI | token 到位本地可跑真源 IT；CI 无 token 全绿 | ✅ `4e75ec3`（600519 行情+财务对公开年报口径区间断言；CI 无 token skip 1 条全绿） |
| 3 | **专项二启动·造数规范成文 + 检索黄金集首批扩容**：造数流程文档（候选来源/评审口径/与 few-shot 隔离的防泄漏机制/轮换纪律）+ 检索黄金集 39→60+（新增案按 normal/paraphrase/literal/boundary/adversarial 五类配比，逐案挂证据）；golden-set-changelog 登记 | 规范成文 Owner 过目；新增案真实 eval 复跑全量通过且旧案零回归；Judge 校准与生成集扩容明确归 W18 | ✅ 规范 [golden-set-authoring](../../eval/golden-set-authoring.md)；v4 扩容 39→60（changelog 登记）；**首跑基线 [w17-golden-v4-baseline](../../eval/retrieval/w17-golden-v4-baseline.md)：正例 43/45=96%、负例 11/15，新增 16 正例全过+旧案零回归；2 条新 adversarial 未过按"挂账在集"先例留集（金融词面 dense 0.53 交叠带 + sparse 标识符网新失败面）——完成定义"新案全过"字面未达，按挂账先例处置并全文披露** |

## 待 Owner 裁决（#1 动工前）

> **Owner 裁决（2026-10-10）**：① token 已就位（申请到手，写入 .env 中）；② #1+#3 双线并行；
> ③ 按推荐执行——Tushare 不可用显式报错，真源链路不给假数据答案；
> 接入形态裁决 **REST 直连**（官方 HTTP API，POST api.tushare.pro）——MCP 面向 AI 宿主
> 自然语言取数，本链路是固定两接口的确定性数据管道，用不上动态发现；
> "官方 MCP 体验"登记为 W21-23 工具周候选（见 capability-map 挂账）。

① **Tushare token 供给**：pro 接口有积分门槛（日行情/利润表各自门槛开工时以
   tushare.pro 官网当日文档为准，不凭记忆）。token 到位前 #1 可做到"实现+契约测试
   全绿、冒烟 IT 待跑"——接受这个半程状态，还是 token 到位才动工？
② **本周组合**：#1+#3 双线（真源收口 + 造数启动），还是先单做 #1、#3 顺延？
③ **故障行为倾向**：Tushare 不可用时报错显式（推荐：真源链路不给假数据答案），
   还是降级回 mock（有 source 标注可溯源，但答案仍是假窗口）？

## 明确不做（防范围蔓延）

- 不做东财/AKShare 等多源聚合与故障切换（单源 Tushare 先行，多源是能力地图另一挂账，
  有真实故障证据再评）
- 不做行情数据的 RAG 化/缓存层（真源直查先行，性能问题出现再说）
- 专项二的 Judge 校准、生成集扩容、引用忠实度强化、内容审核论述补章——归 W18，
  本周只动检索黄金集与造数规范
- 不动 mock 种子数据与既有测试（mock 是测试基座，永远保留）

## 顺序与节奏

裁决落地 → #1 TDD（红：契约测试先写——Mock/Tushare 两实现同断言集；绿：HTTP 层收口
实现）→ #2 冒烟 IT → #3 造数规范（半天）+ 首批扩容复跑。每天 1 项以内，逐项 review。

## 开工口令

读 AGENTS.md + 本文件 + [capability-map](../../capability-map.md)（工具行挂账）
+ `StockDataClient`/`MockStockDataClient` 现状 + [golden-set-changelog](../../eval/golden-set-changelog.md)。
