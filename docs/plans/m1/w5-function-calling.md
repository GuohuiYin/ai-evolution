# W5 计划：Function Calling 工具工程 + M1 验收门

> 对应 PPT：W5「L3 Harness 概念 + 工具工程」。
> 与 W4 的因果链：W4 的 P0 手填数据，W5 让模型自己取——`@Tool` 直接包装 W4 的 `StockDataClient`。

## Step 拆解与退出条件（草案，开工前细化）

### Step 1 · 三个工具上线

| 项 | 内容 |
|---|---|
| 做什么 | `getDailyQuotes`（行情）、`getFinancialSummary`（财务）、`searchAnnouncements`（公告）封装为 Spring AI `@Tool`；mock→真源（Tushare/东财）替换本周完成 |
| 退出条件 | ① 问"茅台股价"模型自主调用工具并基于返回作答 ② 非工具问题不触发调用 ③ 工具描述/参数 Schema 有版本意识（描述质量直接决定 Agent 智商） |

### Step 2 · 工具层 Harness

| 项 | 内容 |
|---|---|
| 做什么 | 错误返回结构化（异常不裸抛给模型）；**来源+时点强制注入**（红线 03 落到工具层）；工具调用审计日志雏形 |
| 退出条件 | ① 数据源故障时模型收到结构化错误并如实告知用户 ② 每条工具产出的数字带 source/asOf ③ 调用参数与结果留痕可查 |

### Step 3 · M1 验收门（铁律：不过不进 W6）

| 项 | 内容 |
|---|---|
| 做什么 | 黄金集从 6 条扩至 20 条（正常/边界/对抗三类），topK 调 5，正式跑 Recall@5 |
| 退出条件 | **Recall@5 ≥ 0.7**，eval 报告归档 `docs/eval/`；不达标则先查摄取管线与分块，不带欠账前进 |

### Step 4 · 摄入管道增强（W3 欠账）

| 项 | 内容 |
|---|---|
| 做什么 | PDF 金融公告解析（Spring AI `PagePdfDocumentReader` 或 Tika）；统一元数据规范落地（数据源/时点/文档类型结构化进 Qdrant payload） |
| 退出条件 | ① 一份真实 PDF 公告可摄入、可检索、可溯源 ② 元数据过滤查询可用（如"只查 2024 年报"） |

## 备注

- 容量风险管理：Step 4 若挤占验收门时间，顺延 W6 开头——**Step 3 不可砍**。
- 真源替换需要 Owner 拍板：Tushare（注册 token）或东财公开接口。
