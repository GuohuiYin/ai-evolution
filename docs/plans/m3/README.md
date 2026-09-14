# M3：最小研究 Loop（W10-W13）

> 对应 PPT Slide 19-20（Agent Loop / 进阶机制）。本页是阶段索引。
> M3 主线一句话：**从"一问一答"进化为"提问→检索→综合→再提问"的多步研究循环**。

## 候选裁决结果（W9-5 裁决，2026-09-10）

四个候选全部纳入，按依赖序执行——**Loop 骨架最早立起来，后续三项作为增强挂到骨架上**：

| 序 | 候选 | 定位 | 证据/依据 |
|---|---|---|---|
| 1 | **显式 ReAct** | M3 主线骨架：thought/action/observation 轨迹显式化，替换当前的隐式工具循环 | M3 命名本体 |
| 2 | **查询改写** | 前置增强：多轮指代消解，让 Loop 在追问下不跑偏 | 3 条失败案例证据 |
| 3 | **MCP Client** | 能力扩展：Agent 以协议方式调用外部 MCP 服务取数（W6 我们是 Server 方，Client 是另一面） | 已立项候选（`4e683c3`） |
| 4 | **混合检索** | 召回侧攻坚：dense+sparse 融合（RRF），BGE-M3 与 Qdrant 原生支持均现成 | 检索黄金集 5 条量化证据（Recall@5=72% 失分集中字面匹配类） |
| 5 | **Reranking 精排**（2026-09-10 A15 补入） | 精排侧攻坚：第一阶段宽松召回（topK 放大至 ~20）→ reranker 重排取前 5；与混合检索互补——混合解决"没捞回来"，精排解决"捞回来排错位/压线误杀"。候选 `bge-reranker-v2-m3`（SiliconFlow 托管，与现有 embedding 同族零新供应商）；两者均走黄金集数据裁决，不达标不启用 | 业界两阶段检索范式；boundary 类边界误判病灶 |

## 周索引

| 周 | 主题 | 文档 | 状态 |
|---|---|---|---|
| W10 | 对话链路地基（会话记忆 + SSE 流式；ReAct 任务 2026-09-11 移入 W11） | [w10-react-loop.md](w10-react-loop.md) | ✅ 完成（2026-09-11） |
| W11 | ReAct 骨架 + 查询改写 + 多轮评测集 + 生成 eval CI 定时回归门 | [w11-query-rewrite-eval-gate.md](w11-query-rewrite-eval-gate.md) | ✅ 完成（2026-09-14，八项全收口，零挂账） |
| W12 | MCP Client + 鉴权与限流（出入双向凭证主题周） | [w12-mcp-client-auth.md](w12-mcp-client-auth.md) | 🔵 计划成文，待开工 |
| W13 | 检索质量攻坚周（混合检索召回侧 + reranking 精排侧，数据裁决去留）+ M3 验收 | [w13-retrieval-two-stage.md](w13-retrieval-two-stage.md) | 🔵 计划成文，待开工 |

> **新会话接续 SOP**（2026-09-11 起）：每周开工开新会话，先让对方读 `AGENTS.md` + 对应周计划文档（含开工口令），再动手。

## 挂账观察清单（随 M3 顺手核查）

- ~~「12987 每个数字代表什么」生成 eval 误拒答（0/0/2，W9-2 跑批发现）~~ ✅ W11 #6 已结案：多轮对照实锤根因是指代未消解→hits=0；改写开启轮满分，关闭轮 2/6 塌方复现原案（w11-multi-turn.md）
- ~~**查询改写证据 #4**~~ ✅ W11 #5/#6 已结案：改写落地（`2c0710a`），多轮 eval 6.0 vs 4.5 量化收益
- embedding 实测对照（bge-m3 vs Qwen3-Embedding-8B）——已挂 W14+ 进阶清单，与混合检索共用"重建基线"流程

## A14 能力域对账（2026-09-10 首次执行，规范见 AGENTS.md A14）

| 能力域 | 现状 | 处置 |
|---|---|---|
| 上下文构建 | ⚠️ 仅单轮：RAG 检索注入 + prompt 模板，无跨轮上下文 | **W10 任务 #0 补地基**（会话记忆原语） |
| 多轮会话 | ❌ **盲区**：无 ChatMemory，每次请求无状态——纯证据驱动规划漏掉的项 | **W10 任务 #0**（`MessageChatMemoryAdvisor` 内存版先行） |
| 记忆（长期） | ❌ 无 | 挂起 W14+：见下方"会话记忆生产级路线" |
| 上下文优化（压缩/摘要/淘汰） | ❌ 无 | 挂起 W14+：待多轮真实运行后按 token 账本数据驱动；对应路线 L3/L4 |

### 会话记忆生产级路线（W10 落 L1，其余 W14+ 按 A14 逐项裁决）

| 阶段 | 形态 | 解决的问题 |
|---|---|---|
| L1 内存版 | `InMemoryChatMemoryRepository` | 语义跑通：同会话多轮可见（**W10 任务 #0**） |
| L2 持久化 | Redis / DB 存历史 | 重启即失忆；K8s 多副本请求落到不同 Pod 断片（云原生第一天，这条来得比别人早） |
| L3 窗口与截断 | 滑动窗口 / token 预算 | 会话变长后成本线性涨、超模型上下文；有 token 账本可依 |
| L4 摘要压缩 | 旧轮次摘要化 + 近期原文保留 | 纯截断丢信息，"记得"与"装得下"的平衡 |
| L5 长期记忆 | 画像/偏好/历史结论向量化，跨会话检索（复用 Qdrant） | Agent Memory 产品形态（业界 Mem0/Letta 所指） |

认知锚点：L1→L2 是工程问题，L3→L4 是成本/质量权衡，L5 是产品形态。
没有真实多轮流量之前，L3/L4 的策略参数无从调起——先跑起来再优化。
| 规划 | ✅ ReAct 显式化 W11 落地（`3285a16`→`b7e188e`，ADR-0015，回归 5.6/6 ≥ 基线） | — |
| 工具 | ✅ 三工具 + MCP Server；MCP Client 为 W12 | 已排期 |
| 检索管线 | ⚠️ 单阶段（topK+阈值一刀切），无精排环节；✅ 查询改写 W11 落地（前置增强） | **W13**：混合检索（召回）+ reranking（精排）两阶段化，数据裁决 |
| 评估 | ✅ 双黄金集 + judge + 多轮评测（W11 #6）+ CI 定时回归门（W11 #7，每天北京 05:23） | — |
| 安全 | ✅ 三红线 + 红队基线 + CVE fail-fast；⚠️ **零鉴权/无限流**（全端点裸奔） | **W12**：API Key 鉴权 + 频率限制，与 MCP Client 组"出入双向凭证"主题周 |
| 可观测 | ✅ traceId + 四级留痕 + token 账本 | 持续 |
| 交互形态 | ⚠️ 整段等待，无流式输出（生产级对话产品 SSE 是标配；ReAct 轨迹推送也依赖它） | **W10**：随对话页改造落地 SSE |

### 可复现性补强（2026-09-10 扫描差距 #6）

- 黄金集版本台账：[docs/eval/golden-set-changelog.md](../../eval/golden-set-changelog.md)——数据集改动先登记再提交，报告分数与数据集版本互相对应
- **能力地图**：[docs/capability-map.md](../../capability-map.md)——A15 差距台账活文档（2026-09-10 首次全维度扫描），后续差距统一在该图维护
- 已挂 W14+ 台账：人工反馈回路、会话记忆 L2-L5、embedding 实测对照、语义缓存、Human-in-the-loop、LLM 可观测平台评估（ADR 候选）、内容审核论述、多 Agent/A2A、引用忠实度强化
- **Agent 轨迹评估**（2026-09-10 扫描新增）：W10 随 ReAct 骨架同步定指标（步数/冗余调用/收敛性），W13 进 M3 验收门——没有它 Loop 质量无度量
- **模型自动降级**（DeepSeek 故障切 Qwen）：W12 韧性主题或 W14+，W12 开工时裁决

## M3 验收门（2026-09-14 定稿，W11 #4）

> 每条 = 口径 + 可跑命令/可看证据 + 阈值 + 落位周。验收时逐项挂证据链接进 m3-acceptance.md（非口头宣布）。

- [ ] **研究 Loop 可演示**：复合问题（数字+文本双查，如"600519 2024 营收多少？顺便查年报工艺描述"）轨迹完整可见
  - 口径：trajectory 事件 ≥2 步、每步 thought/action/observation 齐全、exit=FINAL_ANSWER、终答双要素都作答
  - 命令：`./mvnw spring-boot:run` 后 `curl -N -X POST localhost:18080/ai/chat/stream -d '{"message":"..."}' -H 'Content-Type: application/json'`，或对话页直接提问看轨迹卡片
  - 证据：research-trace 日志段 + SSE 事件流摘录（入 m3-acceptance.md）｜落位：W11 #3 已具备，W13 验收时实录
- [ ] **Agent 轨迹评估指标落地**：三指标有量化口径且可采集
  - 口径（2026-09-14 定）：**步数** = LoopResult.steps().size()；**冗余调用** = 同 tool + 同归一化 input 的重复次数；**收敛率** = 一批运行中 stopReason=FINAL_ANSWER 的占比
  - 采集点：`LoopListener.onComplete`（#2 预留的观测端口，零入侵）；数据源同时可离线解析 research-trace 日志
  - 命令：W13 落地批量采集脚本（docs/scripts/），对黄金集 agent 路由用例输出三指标
  - 阈值：收敛率 = 100%（允许 MAX_STEPS 但须显式登记理由）、冗余调用 = 0｜落位：W13
- [ ] **多轮追问场景生成 eval 不回归**（查询改写生效的量化证据）
  - 口径：改写开/关两轮跑批 multi-turn 类别（W11 #6 新增 ≥6 条），开启轮均分严格高于关闭轮；其余类别不降级
  - 命令：`AI_EVAL_GENERATION_ENABLED=true AI_EVAL_GENERATION_CATEGORIES=multi-turn ./mvnw spring-boot:run`（两轮差 `AI_REWRITE_ENABLED`——该开关随 W11 #5 落地）
  - 证据：两轮对比表入 docs/eval/generation/w11-multi-turn.md｜落位：W11 #5/#6
  - 进度（2026-09-14）：multi-turn 两轮对比 ✅ 达成（开 6.0/6 > 关 4.5/6）；"其余类别不降级"当时只跑了 multi-turn 类别，W13 验收时全量复跑补齐后打勾
- [ ] **至少一个外部 MCP 服务被我们的 Agent 真实调通**
  - 口径：Agent 以 MCP Client 身份调用外部 MCP 服务取到真实数据并用于回答，回答含溯源
  - 证据：调用日志（tool-audit）+ 回答摘录；候选服务 W12 开工时定（公开行情/财经 MCP 优先）｜落位：W12
- [ ] **混合检索 / rerank 上线与否由黄金集回归数据裁决**（同 few-shot 裁决机制：事先写死标准）
  - 口径：检索黄金集全量，dense 基线 vs 混合检索 vs 混合+rerank 三轮 Recall@5
  - 阈值（2026-09-14 写死）：启用条件 = Recall@5 ≥ 80%（基线 72% +8pp）且五条失分用例无一恶化；不达标则不启用并记录 why-not
  - 命令：`./mvnw test -Dtest=GoldenRetrievalEvalIT`（配置切换检索模式）｜落位：W13
