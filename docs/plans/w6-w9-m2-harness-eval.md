# M2 计划概要：Harness + MCP + Eval（W6-W9）

> 对应 PPT Slide 18。细节计划在各周开工前细化，本页锚定范围与验收门。
> M2 是拉开差距的阶段——"市场上一半的 AI 工程师到不了这一层"（PPT 原话）。

## 周主题与核心交付

| 周 | 主题 | 核心交付 | 深度目标 |
|---|---|---|---|
| W6 | MCP 协议精读 + 动手 | **P2：业务能力封装为 MCP Server**，被 Claude Code / Cursor 真实调用 | 会调 |
| W7 | 安全 + 成本横切面 | 鉴权 + 审计日志；**注入攻击实验**（公告语料藏指令，验证护栏拦截率）；合规护栏；prompt caching | 会调 |
| W8 | 评估（Eval-first） | **P4-lite：黄金集 30-50 条**（W3 雏形扩展）；一键评分（准确性+遵循度+溯源完整性）；Micrometer LLM 调用指标 + 一页成本账 | 会调 |
| W9 | 缓冲周 + M2 验收 | 作品集整理；README 架构图；eval 报告归档 | — |

## M2 验收门（三件缺一件就留在 W9 补齐）

- [ ] MCP Server 被外部客户端（Claude Code / Cursor）调通
- [ ] eval 一键可跑
- [ ] 成本账一页纸（W4-Step4 的手算账单在此升级为体系）

## 我们的既有优势（超前项）

- eval 雏形 W3 已就位 → W8 从 0 到 1 变成从 6 条扩到 30-50 条
- actuator/metrics 端点 W1 已暴露 → W8 只需补 GenAI 语义指标（token 计量）
- 金融三红线已在代码层部分落地 → W7 注入实验有现成护栏可测

## 关键概念锚点

- MCP 在 L2 是信息管道，W6 之后升级为受控执行通道（PPT Slide 11）
- 注入攻击素材：金融公告天然混入指令式语言——选金融域的红利之一

## W5 扫描遗留（W6/W7 裁决）

- `ChatController` 依赖不对称：`/ai/chat` 走 `ChatService` 接口，`/ai/agent` 直注具体类 `AgentChatService`。两服务语义不同（纯 RAG 固定管道 vs 工具增强 Agent），W6/W7 引入查询理解/路由层时统一裁决：是否抽象统一的对话路由接口
- 黄金集负例分层：`out-of-domain`（检索层拒召回）与 `in-domain-unanswerable`（检索命中、生成层拒答）拆分类别，后者改由生成层评估覆盖——见 docs/eval/m1-gate-recall.md 的设计讨论
