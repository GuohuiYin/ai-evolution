# Tushare 官方 hosted MCP 体验笔记（W24 #4b 销项）

日期：2026-10-27 ｜ 状态：✅ 跑通销项（W17 挂账，顺延两周后本周清仓）

## 是什么

Tushare 官方托管的 MCP Server：`https://api.tushare.pro/mcp/?token=<Tushare token>`
（Streamable HTTP 形态，token 直接编在 URL 里；注意裸 `/mcp` 会 307 到 `http://.../mcp/`，
客户端须跟随重定向）。接入指南：[tushare.pro/document/1?doc_id=463](https://tushare.pro/document/1?doc_id=463)。

## 跑通记录（curl 直连，JSON-RPC over SSE）

1. `initialize` → serverInfo `tushare 0.0.1`（"提供A股股票、基金等金融数据查询能力"）
2. `tools/list` → **258 个工具**：A股行情/财务/资金流/龙虎榜、港股美股、基金期货债券、
   宏观（GDP/CPI/Shibor）、新闻公告、量化因子库——基本是 Tushare 全量接口的 MCP 化
3. `tools/call daily(ts_code=600519.SH, 20261019~20261027)` → 真实日线 JSON
   （2026-10-26 收盘 1349.98，涨 1.12%），字段与 REST `daily` 接口一致

## 与 REST 确定性管道的分工边界

| 维度 | 本系统 REST 管道（tool/ 包） | Tushare hosted MCP |
|---|---|---|
| 形态 | 4 个精选工具，字段裁剪、口径治理过 | 258 个原始接口，全字段 JSON 直出 |
| 调用决策 | 显式路由 + 工具审计留痕（tool-audit） | AI 宿主自然语言自主选接口 |
| 红线语义 | 进 RAG/Agent 链路，受溯源与拒答纪律约束 | 无治理层，原始数据直接进上下文 |
| token 成本 | 低（字段按需裁剪） | 高（全字段，长结果集） |
| 适用 | **线上问答主链路**（可评测、可审计、可控） | 探索性取数 / 新数据面快速验证 / AI 编程工具里随手查 |

**结论**：两条链路互补不替代。REST 管道继续扛线上主链路（确定性 + 红线 + 评测闭环）；
hosted MCP 定位「免开发的广覆盖备用通道」——未来若要扩数据面（如宏观、港股），
可先用 MCP 快速验证价值，值得入主链路再做 REST 化治理。本周不接入应用，仅销项登记。

## 风险与注意

- token 编在 URL 里：日志/配置管理须防泄露（本机 .env 已隔离，A5 纪律同）
- 307 到 http 的跳转行为：生产接入时确认最终端点是否强制 https
- 接口权限仍走 Tushare 积分体系，与 REST 共用同一 token 额度
