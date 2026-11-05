# 主题复习索引（Revision Guide）

> 2026-11-05 成文（Owner 复习需求驱动：回顾全课程「对标业界标准的 Agent 架构、方案设计与实现」）。
> 定位：课程地图按**时间线**组织，本索引按**知识主题**组织——每个主题固定四栏：
> 业界标准做法（对照系）/ 本项目设计（ADR）/ 实现与验证（代码锚点 + eval 证据）/ 教训（retro）。
> 素材全部来自既有文档，本索引只做汇编与指路，不复制正文（防双写腐化）。
>
> **维护规则**：每周收官顺手补一行（与课程地图 A16④ 同机制）；超过两周未更新视为失效。

## 使用说明

- **30 分钟快读**：本文件七个主题的「业界标准 + 教训」两栏 + 课程地图的五条暗线段。
- **半日深读**：每主题沿「ADR → eval 报告 → retro」顺序点进去，ADR 回答 why，
  eval 报告回答 how-well，retro 回答 what-went-wrong。
- 术语卡：[term/terminology.md](term/terminology.md)；逐周记忆点：[plans/course-map.md](plans/course-map.md)。

## 一、Agent 架构与 Loop

| 栏 | 内容 |
|---|---|
| 业界标准 | ReAct 显式轨迹是生产级 Agent 的基线形态；复杂任务用规划-执行-复核的角色切分（plan/build/review）；长任务需要持久化编排（工作流引擎）而非进程内状态机；关键节点留人工审批断点（HITL）。参考：[agent-engineering-roadmap](notes/agent-engineering-roadmap.md)、[a2a-protocol](notes/a2a-protocol.md) |
| 本项目设计 | 显式 ReAct（[ADR-0015](adr/0015-explicit-react-loop.md)）→ 三角色管线打回回流（W22 设计先行：[w21-l4-design](plans/advanced/w21-l4-subagent-and-skill-design.md)）→ Temporal 三件套（[ADR-0019](adr/0019-temporal-observability.md)）→ HITL 断点三处留痕（W23 #1） |
| 实现与验证 | 57 案双臂对照 +0.33 后 pipeline 切默认引擎（[w22-pipeline-dual-arm](eval/generation/w22-pipeline-dual-arm.md)）；HITL 双路径真机演示（[w23-hitl-demo](eval/generation/w23-hitl-demo.md)）；Skill 渐进披露两轮实测 2.0%/9.7% 未达 20% 守线，止步登记（负结果留档） |
| 教训 | 协议违背的静默兜底 = 泄漏出生地（W18-20 三连案）；「不上」也是数据裁决的成果（Skill 两次止步）；打回循环要真打回过才算接通（真机冒烟红线案首轮打回） |

## 二、RAG 检索管线（摄入→分块→向量化→召回→精排→生成→引用）

| 栏 | 内容 |
|---|---|
| 业界标准 | 混合检索（向量+关键词）是召回率主力手段；rerank 精排常见但非必选项；查询改写承担多轮指代消解；摄入侧增量与幂等是生产基线。参考：[graphrag 笔记](notes/graphrag.md)（含不做的边界） |
| 本项目设计 | 手动编排拒黑盒（[ADR-0006](adr/0006-manual-rag-orchestration.md)）；两阶段检索（[ADR-0017](adr/0017-retrieval-two-stage-design.md)）；增量摄入 manifest 自愈（[ADR-0012](adr/0012-incremental-ingestion-manifest.md)）；改写护栏剥离注入标识符（W28 #1） |
| 实现与验证 | 混合检索 +21pp 裁决启用（[w13-hybrid-eval](eval/retrieval/w13-hybrid-eval.md)）；rerank +0pp 裁决不启用；chunk-size 三轮对照维持 800；多样性配额（[w23-diversity-quota](eval/retrieval/w23-diversity-quota.md)）；零召回防线 A/B 探针 3/3 vs 0/3（[w24-zero-recall-retry](eval/retrieval/w24-zero-recall-retry.md)）；案 30 修复双臂零回归（[w28-rewrite-guard-eval](eval/generation/w28-rewrite-guard-eval.md)） |
| 教训 | 两个银弹（chunk-size/rerank）被自家数据证伪——每个开关都要过数据裁决；防线也会误杀（W8「标识符全中才放行」斩掉正解块，修防线不如断注入源）；截断必须有声（W25 sparse 500 静默截断教训）；阈值悬崖会制造零召回（0.4817 惜败 0.5） |

## 三、评估体系（本课程最硬的一块）

| 栏 | 内容 |
|---|---|
| 业界标准 | 没有 eval 的 RAG 是裸奔；LLM-as-a-Judge 需锚定与校准，judge 自身的可信度也要被量化；黄金集要防泄漏、防腐烂、可轮换 |
| 本项目设计 | eval 门机制（[ADR-0007](adr/0007-eval-gate-mechanism.md)，数据裁决机制原点）；judge 隔离设计（[ADR-0014](adr/0014-llm-as-a-judge-isolated.md)）；prompt 改动纪律门 A13；造数规范与轮换四纪律（[golden-set-authoring](eval/golden-set-authoring.md)） |
| 实现与验证 | 检索 69 条 + 生成 58 条双黄金集；judge 校准链 95.5%→36.7%（时效破产）→63.3%→83.3%→88.9% 首过守线（[w27 收卷](eval/generation/w27-judge-calibration.md)）；rubric 维度归属澄清（W28 #2）；反馈回路最小环 👍/👎→候选区（W24 #3） |
| 教训 | 评估设施本身的可信度也要被评估（校准会过期、参照会腐烂——v3.4「数据源切换型腐烂」首例）；修尺必须带对照案复评（涉案翻分、满分案不动）；few-shot 49:49 打平敢留负结果 |

## 四、安全与合规

| 栏 | 内容 |
|---|---|
| 业界标准 | 安全响应头套件 / CORS 显式策略 / 认证授权走 OAuth2+OIDC 与 RBAC / SSRF egress 控制是 Web 层基线（W29/W30 主题，现状差距逐项挂证据见 [W29 计划](plans/advanced/w29-web-security-hardening.md) 输入盘点表） |
| 本项目设计 | 三红线代码化单点（A12）；红队先行（先攻击后防御）；安全配置显式化+测试锁定（[ADR-0013](adr/0013-explicit-security-config-with-test-lock.md)）；红线告警先行再拦截（[ADR-0011](adr/0011-redline-alert-only.md)） |
| 实现与验证 | 红队 10 用例基线（[w7-redteam](security/w7-redteam-baseline.md)）；API Key 默认拒绝 + 限流 429（W12）；MCP fetch 白名单（[ADR-0016](adr/0016-mcp-client-fetch-whitelist.md)）；消息长度/请求体双层上限 400/413（W28 #3） |
| 教训 | 「看得见」必须先于「拦得住」（第一版红线只告警不拦截）；安全配置不吃库默认值；内容审核论述结案——低风险语料也要三面登记（[moderation-minimal-defense](security/moderation-minimal-defense.md)） |

## 五、可观测与成本

| 栏 | 内容 |
|---|---|
| 业界标准 | OpenTelemetry GenAI 语义约定是新兴事实标准；token 成本台账 + 预算熔断是成本工程基线；平台选型 Phoenix vs Langfuse 看运维成本（[对照笔记](notes/llm-observability-phoenix-vs-langfuse.md)） |
| 本项目设计 | 可观测三面分工（[ADR-0019](adr/0019-temporal-observability.md)）→ OTel+Phoenix 落地（[ADR-0020](adr/0020-llm-observability-otel-phoenix.md)，修订 0019「不上 OTel 栈」）；模型路由分档备而不用（W25 #3） |
| 实现与验证 | traceId 四级留痕（W17 起）；JSONL 台账重启不丢 + 日预算熔断 fail-closed 实撞两次（W26）；pro 档成本 15-20x 零质量收益裁决不升档（[定档报告](eval/selection/agent-tier-v1.md)）；前缀缓存白拿 28% |
| 教训 | 熔断的账本不能依赖观测栈存活；探针必须逐行对齐生产链（测「想象中的语义」会产出方向性错误结论）；预算闸重启可绕过——绕过路径要登记 |

## 六、韧性与运维

| 栏 | 内容 |
|---|---|
| 业界标准 | failover ≠ 高可用（备模型有自己的延迟与配额特征）；故障注入 + 分层诊断 SOP 是值守基线；无人值守窗口的 HITL 缺席策略必须显式化 |
| 本项目设计 | FailoverChatModel 装饰器透明切换（W12 #4）；HITL 超时 fail-closed（W23）；24h 无人值守顺延包（W26 完成定义先行） |
| 实现与验证 | 双故障注入按 SOP 计时：Qdrant 2 分钟 / DeepSeek 3 分钟定位（[w26 运行报告](ops/w26-unattended-run-report.md)）；正常窗口可用率 98%；净效率 18 次回答/元 |
| 教训 | 熔断遮蔽效应（预算闸先于模型层，byModel 无痕迹）；failover 备臂撞破 120s 活动预算（W26 retro #1，修复挂账 W31）；参照腐烂二次（judge 无错参照先腐） |

## 七、上下文与记忆

| 栏 | 内容 |
|---|---|
| 业界标准 | 会话记忆分级（窗口→持久化→摘要→跨会话）按流量证据逐级上马；上下文压缩/语义缓存需真实流量数据驱动，不预建 |
| 本项目设计 | 记忆 L1 窗口（W10）→ L2 MySQL 持久化（W21 #1③，[ADR-0021](adr/0021-memory-strategy.md)）；改写装饰器接通 pipeline 证据包（W27 #2）；L3-L5 与压缩/语义缓存全部数据裁决不做或挂账（触发条件在案） |
| 实现与验证 | 证据包接入会话上下文后 multi-turn 5.0→5.7（[w27-multiturn-context-fix](eval/generation/w27-multiturn-context-fix.md)）；p99 prompt 22K 距窗口远（不做的证据）；单轮会话 56%（L5 不做的证据） |
| 教训 | 「具备切换能力」≠「已切换」（retro #7：开关没打开能力等于不存在）；多轮无记忆曾是监控盲区——没有证据 ≠ 没有问题（A14 来历） |

## 附：全程通用方法论（五周以上反复兑现的）

1. **数据裁决机制**（W8 首创）：一切开关启用/翻默认都要 A/B 或双臂证据，含两次以上否定答案（rerank/bm25/Skill/few-shot）——机制可信的证明是否定结果也被忠实记录。
2. **单变量对照**：新机制只是嫌疑名单第一名（W27 案 30：asOf 重排嫌疑双跑排除后才坐实召回漏检）。
3. **证据有保质期**：校准/参照/观测文档都会腐烂，四纪律 + 如实标注补记是防腐剂。
4. **欠账不过周 + 挂账带触发条件**：防遗忘 ≠ 要动手，每个「不做」都写清重评扳机（[w28-plus-todo-candidates](w28-plus-todo-candidates.md) 是当前总闸）。
