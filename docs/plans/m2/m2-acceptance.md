# M2 验收记录（Harness + MCP + Eval）

> 验收日：2026-09-09（W9-1）。验收门来自 m2 阶段索引（README.md）：三件缺一件留 W9 补齐。
> 结论：**三件全过，M2 收官**。以下逐项给出可复核证据，非口头宣布。

## 验收门①：MCP Server 被外部客户端调通 ✅

| 证据 | 位置 |
|---|---|
| 协议级冒烟脚本（initialize → initialized → tools/list → tools/call 全链路断言） | [docs/scripts/mcp-smoke.sh](../../scripts/mcp-smoke.sh) |
| Codex 客户端接入配置（`[mcp_servers.ai-evolution]`） | `~/.codex/config.toml:74` |
| MCP 协议精读笔记（动手前先读懂协议） | [docs/notes/mcp-protocol.md](../../notes/mcp-protocol.md) |
| 架构决策 | [ADR-0009](../../adr/0009-mcp-server-exposure.md)（Streamable HTTP，裸通优先） |

复验方式：服务启动后 `docs/scripts/mcp-smoke.sh`（默认 http://localhost:18080/mcp），退出码 0 即全链路通过。

## 验收门②：eval 一键可跑 ✅

| 评估 | 命令 | 基线 |
|---|---|---|
| 检索黄金集（39 条） | `AI_EVAL_ENABLED=true ./mvnw spring-boot:run` | Recall@5=72%，[w8-retrieval-baseline](../../eval/retrieval/w8-retrieval-baseline.md) |
| 生成黄金集（16 条 + LLM judge） | `AI_EVAL_GENERATION_ENABLED=true ./mvnw spring-boot:run` | 总体 5.3/6，judge 锚定 5/5，[w8-generation-eval](../../eval/generation/w8-generation-eval.md) |

支撑机制：CI 侧 `GoldenRetrievalEvalIT`（哈希替身，normal 正例回归）每次构建强制；架构决策见 ADR-0007（验收门）与 ADR-0014（judge 隔离）。

## 验收门③：成本账一页纸 ✅

[docs/cost/w8-cost-report.md](../../cost/w8-cost-report.md)：实测 57 次调用 ¥0.31；单次问答 ¥0.0054；
一轮生成 eval ¥0.17；月度预估 ¥37；缓存命中率 55.8%。

## M2 期间的关键演化（超出验收门的部分）

- 安全基线成体系：红队 10 用例 → W7 双修复 → W8 CVE fail-fast + 调用上限（ADR-0013 显式化原则）
- Harness 四核心盘点（m2 阶段索引（README.md） 仪表盘）：Constrain/Inform 成型，Verify 本周补齐，Correct 待 M3
- 两个 eval 逮住的真实缺陷入 M3/W9 backlog：agent 数据缝合（W9-2 修复）、12987 检索盲区（M3 混合检索候选）

## 签署

W9-1 复核通过。下一里程碑：M3 最小研究 Loop（W10-W13），候选裁决在 W9-5。
