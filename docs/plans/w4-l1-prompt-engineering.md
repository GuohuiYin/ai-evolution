# W4 作战计划：L1 Prompt 工程补课（P0 个股分析）

> 背景：W1-W3 实战中对账发现 L1（Prompt 工程）整层被跳过，违反"禁止跳层"铁律。
> W4 为补课周，目标是把 L1 的退出信号做实，同时清零 W1 两项欠账（模型选型笔记、Token 账单）。
> 数据源决策：**mock 先行**，链路跑通后再换 Tushare/东财真源（接口形状不变，替换实现即可）。

## 本周业务场景

**P0 个股历史表现分析**：输入股票名称/代码，输出结构化的历史表现分析（而非自由文本）。
这是 W5 工具调用的动机来源——W4 手填的数据，W5 让模型自己取。

## Step 拆解与退出条件

### Step 1 · 数据源客户端（mock 先行）

| 项 | 内容 |
|---|---|
| 做什么 | 定义 `StockDataClient` 接口（`getDailyQuotes`、`getFinancialSummary`），首实现为 `MockStockDataClient`（内置茅台/宁德时代 2024 年报口径的示例数据，**每条数据带数据源+时点**，红线 03） |
| 设计要点 | 接口即契约：mock 与未来 Tushare 实现同形状；数据类用 Java record； seeded 固定数据保证测试确定性 |
| 退出条件 | ① 接口 + mock 实现 + 单测全绿 ② mock 数据每条含 source/asOf 字段 ③ 换真源时调用方零改动（以接口注入验证） |

### Step 2 · P0 结构化输出 + Prompt 版本化

| 项 | 内容 |
|---|---|
| 做什么 | ① 新建 `resources/prompts/` 目录，System Prompt 从 `RagChatService` 硬编码迁出为 `v1` 版本文件（运行时加载）<br>② P0 分析 Prompt `v1`：CO-STAR 六要素 + few-shot 示例<br>③ Spring AI 结构化输出（`.entity()`）映射为 `StockAnalysisReport` record |
| 设计要点 | prompt 是资产不是字符串常量——进 git 版本管理，文件名带版本号；结构化输出 schema 即 API 契约 |
| 退出条件 | ① 硬编码 prompt 清零 ② `POST /ai/analyze` 返回结构化 JSON（非自由文本）③ few-shot 示例与评估集隔离（防泄漏）④ 单测覆盖 entity 映射 |

### Step 3 · 改前/改后效果对比（L1 退出信号）

| 项 | 内容 |
|---|---|
| 做什么 | 用同一批 5 个分析问题，分别跑"裸 prompt"（v0，一句话指令）与"工程化 prompt"（v1），记录输出质量差异到 `docs/eval/p0-prompt-comparison.md` |
| 设计要点 | 这就是 L1 的退出信号实证："场景准确率达基线且优化收益递减"要能拿数据说话 |
| 退出条件 | 对比文档含：同一输入的 v0/v1 输出并排、结构化完整率、人工点评；能讲清"few-shot 到底改善了什么" |

### Step 4 · 模型选型对比实验 + Token 账单

| 项 | 内容 |
|---|---|
| 做什么 | DeepSeek vs Qwen 各跑 10 条真实业务问题（复用 Step 3 的问题集 + RAG golden-set），记录能力/延迟/token 消耗三维数据，手算成本 → 产出 `docs/model-selection-v1.md` |
| 设计要点 | 一次实验补两个 W1 欠账；Qwen 走 DashScope 或 SiliconFlow 的 Qwen 模型（Spring AI OpenAI 兼容协议，配置切换即可，零代码改动——顺便验证多供应商抽象的价值） |
| 退出条件 | ① 选型笔记含实测数据表格 ② 能讲清"为什么主力选 A 不选 B" ③ 列出所选模型的三类典型失效场景预判 |

## 本周验收门（W4 出口）

- [ ] P0 可演示：`/ai/analyze` 输入"贵州茅台"返回结构化分析报告（数据来自 mock，来源时点齐全）
- [ ] prompt 资产化：v0/v1 两版本 + 效果对比文档
- [ ] 《模型选型笔记 v1》含 DeepSeek/Qwen 实测对比 + Token 账单
- [ ] 全部测试绿，spotless 过，集群部署冒烟通过（云原生第一天）

## 风险与备注

1. **Qwen 接入**：若 DashScope 需额外注册，备选 SiliconFlow 上的 Qwen 模型（已有 API Key，零新增账号）。
2. **容量控制**：Step 2 和 Step 3 若超一周容量，Step 4 可顺延至 W5 开头——但 Step 1-3 是 L1 核心，不可砍。
3. **与 W5 的衔接**：W5 的 3 个 `@Tool`（行情/财务/公告）将直接包装本周的 `StockDataClient`，mock→真源的替换也在 W5 完成。
