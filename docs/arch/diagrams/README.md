# 架构图集（C4 标准）

> 按 [C4 model](https://c4model.com) 分层组织。2026-10-28 首版（W24 收官态取证自代码与
> application.yml，非凭记忆）。**维护规则**：架构性变更（新增外部依赖、新域、引擎/通路
> 增减）当周同步改图；每图头部标注截止时间点，超过两周未与代码对齐视为失效。

## 层级导航

| 层级 | 图 | 回答的问题 |
|---|---|---|
| L1 | [系统上下文](c4-1-system-context.md) | 系统给谁用、依赖谁、被谁依赖 |
| L2 | [容器图](c4-2-container.md) | 由哪些进程/存储组成，各自技术选型与职责 |
| L3 | [组件图 · chat 入口域](c4-3-component-chat.md) | 请求怎么进来、怎么分流、记忆在哪挂载 |
| L3 | [组件图 · loop 研究循环域](c4-3-component-loop.md) | 三角色管线怎么编排、Temporal 在哪层、HITL 怎么挂 |
| L3 | [组件图 · rag 检索域](c4-3-component-rag.md) | 摄入/检索两条链，每个数据裁决过的开关在哪 |
| L3 | [组件图 · infra 横切域](c4-3-component-infra.md) | 安全/记忆/韧性/审计/技能/合规怎么横切所有请求 |
| L4 | （刻意不画） | C4 官方建议：代码级图不手工维护，直接读代码——本仓包结构即文档（ADR-0003 按子域分包） |
| 补充 | [横切视图](cross-cutting-view.md) | 一次请求穿过的关注点五层分层——每层谁把守、在哪留痕 |
| 补充 | [纵切视图](vertical-slices.md) | 四条端到端链路：RAG 问答 / 研究循环含 HITL / 反馈闭环 / eval 生产线 |

## 与既有架构文档的关系

- [advisor-chain](../advisor-chain.md)：Advisor 链源码精读（W20）——Spring AI 框架层
  内部视角，本图集是应用层视角，两者互补。
- [ADR 列表](../../adr/)：每张决策卡记录「为什么这么选」；本图集记录「现在长什么样」。
  图与 ADR 冲突时先查代码，再回改图（代码是唯一真源）。
- README 根架构图（mermaid 数据通路视角）继续保留：那是数据流视角，本图集是
  C4 静态结构视角。

## 渲染说明

全部使用 mermaid 原生 C4 标记（C4Context/C4Container/C4Component），VS Code
Markdown Preview Mermaid Support 或 mermaid.live 均可渲染。
