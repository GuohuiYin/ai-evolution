# M2：Harness + MCP + Eval（W6-W9）

> 对应 PPT Slide 18。本页是阶段索引：锚定范围与验收门，每周计划独立成文（见下表）。
> M2 是拉开差距的阶段——"市场上一半的 AI 工程师到不了这一层"（PPT 原话）。

## 周索引

| 周 | 主题 | 文档 | 状态 |
|---|---|---|---|
| W6 | MCP 协议精读 + 动手 | [w6-mcp-server.md](w6-mcp-server.md) | ✅ 已完成 |
| W7 | 安全 + 成本横切面 | [w7-security-cost.md](w7-security-cost.md) | ✅ 已完成 |
| W8 | 评估（Eval-first） | [w8-eval-hardening.md](w8-eval-hardening.md) | ✅ 全满贯 |
| W9 | 缓冲周 + M2 验收 | [w9-m2-acceptance.md](w9-m2-acceptance.md) | 🔵 进行中 |
| — | **验收记录** | [m2-acceptance.md](m2-acceptance.md) | ✅ 三件全过 |

## M2 验收门（三件缺一件就留在 W9 补齐）

- [x] MCP Server 被外部客户端调通（Codex / Inspector 实证）
- [x] eval 一键可跑（检索 + 生成双 Runner）
- [x] 成本账一页纸

## 既有优势（超前项，规划时的判断）

- eval 雏形 W3 已就位 → W8 从 0 到 1 变成扩充
- actuator/metrics 端点 W1 已暴露 → W8 只需补 token 计量
- 金融三红线已在代码层部分落地 → W7 注入实验有现成护栏可测

## 关键概念锚点

- MCP 在 L2 是信息管道，W6 之后升级为受控执行通道（PPT Slide 11）
- 注入攻击素材：金融公告天然混入指令式语言——选金融域的红利之一

## Harness 四核心健康度仪表盘（M2 收官态）

| 核心 | 收官现状 |
|---|---|
| Constrain | ✅ 三红线 + 输入/输出约束 + CVE fail-fast + 工具调用上限（ADR-0013） |
| Inform | ✅ RAG + 工具双通道 + 增量摄入 manifest（ADR-0012） |
| Verify | ✅ 检索 39 条 + 生成 16 条 + LLM judge（ADR-0014）；W11 落定时回归门 |
| Correct | ⚠️ W9 起步（agent 反缝合规则）；M3 系统化（查询改写 / 红线降级 / ReAct 轨迹） |

顺序原则：先能判定对错（Verify），再谈自动修正（Correct）——没有 Verify 的 Correct 是盲人修车。

## W5 扫描遗留的裁决结果

- `ChatController` 依赖不对称 → **W6 已裁决落地**：统一入口 + 规则路由（ADR-0010）
- 黄金集负例分层 → **W8 已落地**：in-domain-unanswerable 归生成层黄金集
