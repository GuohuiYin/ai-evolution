# 项目亮点清单（M2 收官 · 2026-09-10）

> 定位：对外可展示的项目成果索引。每条亮点挂 commit 哈希与报告链接——**所有声称都可验证**。
> 读者画像：任何想评估这套 Agent 工程化实践的工程师 / 未来的自己。读法：先看亮点一句话，需要深挖再点证据。

## 一、评估体系（最硬的一块）

业界共识"没有 eval 的 RAG 是裸奔"，本项目把评估做成了基础设施而非报告：

| 亮点 | 证据 |
|---|---|
| 双黄金集：检索 39 条（Recall@5=72% 基线）+ 生成 16 条（LLM judge 三维评分） | [w8-retrieval-baseline](eval/retrieval/w8-retrieval-baseline.md) · [w8-generation-eval](eval/generation/w8-generation-eval.md) · `08af121` `a187487` |
| LLM-as-a-Judge 带锚定（judge 自身 5/5 才信它的分）——评估评估者的 metaeval 意识 | `b926412` · [ADR-0014 judge 隔离设计](adr/0014-llm-as-a-judge-isolated.md) |
| prompt 改动纪律门 A13：动 prompt 必须附生成 eval 跑批摘要才能 commit | `c989436` 立规 → `f444792` 首次执行（反缝合规则修复，冰淇淋案例 0/0/1 → 2/2/2） |
| few-shot A/B 对照实验：49:49 打平 → 裁决不启用。**敢留负结果** | `3756526` 设施 → `7e9cc26` 裁决报告 |
| 选型评测与质量回归分层管理；embedding/向量库选型报告含重评触发条件 | `a9a151e` 目录重组 · [embedding](eval/selection/embedding-selection-v1.md) / [向量库](eval/selection/vectorstore-selection-v1.md) 选型报告 |

## 二、Harness 四核心落地实证

Harness 工程（Constrain / Inform / Verify / Correct）不是概念，逐条有代码：

| 核心 | 本项目落地 | 证据 |
|---|---|---|
| **Constrain**（约束行为） | 金融三红线代码化：强制免责声明（单点 A12）/ 拒买卖建议 / 数字必溯源；prompt 强约束 + schema 输出 | `d7d3cc6` 红线单点化 · [README 三红线](../README.md#三条设计红线写进代码全项目生效) |
| **Inform**（供给上下文） | RAG 手动编排（非 Advisor 黑盒）：bge-m3 向量化 + Qdrant 元数据过滤 + 空检索硬拒答；prompt 模板版本化资产 | ADR-0006 RAG 手动编排 · `5988d3b` PDF 摄入 + 元数据规范 |
| **Verify**（验证输出） | 双黄金集回归 + LLM judge 锚定 + 检索 eval 进 CI（Testcontainers） | 见"一、评估体系" · `c0333c3` M1 验收门 85.7% 过堂 |
| **Correct**（纠偏机制） | 反缝合规则：发现"部分相关数据被包装成答案"漏洞 → prompt v2 修复 + eval 验证恢复 | `f444792`（W9-2 完整闭环：发现→修复→验证→纪律门） |

## 三、安全基线

| 亮点 | 证据 |
|---|---|
| 红队先行：先攻击（10 用例基线）后防御，P0 双修复（MCP 错误脱敏 + 入参上限） | `b66e838` 攻击报告 → `28c52cf` 修复 · [w7-redteam-baseline](security/w7-redteam-baseline.md) |
| CVE-2026-59318 工具解析兜底显式关闭 + 测试锁定（防配置漂移） | `5bb84c9` · [ADR-0013 安全配置显式化](adr/0013-explicit-security-config-with-test-lock.md) |
| 纵深防御：工具调用次数上限 + AOP 审计留痕 | `efff9b9` · ADR-0008 审计切面 |
| 红线服务端告警（ADR-0011）：红线触发不止拒答，还留告警痕迹 | `4728c10` |

## 四、工程素养（Java 架构师的基本盘）

| 亮点 | 证据 |
|---|---|
| 协议贯通：MCP Server 对外暴露三工具，Inspector/Codex 真实调通；动手前先出协议精读笔记 | `e091428` 精读 → `e60bcb8` 实现 → `2539108` 调通证据 · [ADR-0009](adr/0009-mcp-server-exposure.md) |
| 云原生第一天：Minikube + ConfigMap/Secret 分层 + 探针 + 优雅停机 + Jib | [k8s/README.md](../k8s/README.md) · [ADR-0002 Jib](adr/0002-image-build-with-jib.md) |
| 可观测全链路：traceId 串链 + 四级留痕 + prompt/response 日志（开关受控）+ token 成本账本 | `5d84883` `b70d827` `32b3865` |
| 成本意识：一页成本账——单次问答 ¥0.0054，一轮生成 eval ¥0.17 | `3c115e0` · [w8-cost-report](cost/w8-cost-report.md) |
| 增量摄入：SHA-256 manifest 三类分派，未变文件零 embedding 调用 | `8d36249` `7ffe685` · [ADR-0012](adr/0012-incremental-ingestion-manifest.md) |
| 领域分包前置（不过载就拆）+ 面向接口 + ArchUnit 架构守护 | `46f7300` `64ac5a8` `dff04fd` · [AGENTS.md](../AGENTS.md) |

## 使用说明

- 叙事线索参考：若向他人介绍本项目，一条自然的线索是——反缝合规则案例（`f444792`，一次完整的"发现→修复→验证→立规"闭环）→ 双黄金集 → 红队先行 → MCP 协议贯通
- 所有 commit 哈希可在本仓库 `git show <hash>` 直接查验
- M3 阶段本文件随里程碑更新（机制：里程碑必产验收记录，DoD 第⑦条）
