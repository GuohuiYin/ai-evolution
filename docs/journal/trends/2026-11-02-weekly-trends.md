# 每周 AI 业界扫描 · 2026-11-02

> 扫描窗口：2026-10-26 ~ 2026-11-02（定时任务周一 14:00 正常触发，连续第二期）
> 项目坐标：W25 已收官（OTel GenAI + Phoenix 落链 / 成本台账 + 预算熔断 fail-closed / 模型路由机制备而不用 / sparse 重评裁决 bm25 不切换 / 反馈回路首例转正，黄金集 58 案）；W26 进行中——24h 无人值守运行已跑通（窗口 10:27–12:53，正常窗口可用率 98%，双故障注入定位 2/3 分钟，熔断两撞留痕），L4 运营验收四条全过，任务 5「最新口径优先」缺陷族修复进行中。ADR-0020（Phoenix 先行）与 ADR-0021（记忆策略：向量起步 + 读写分离 + 可治理）本周新立。

---

## 一、全景速览（本周业界在发生什么）

- **资本市场地标事件**：Anthropic 正式递交 S-1 申请纳斯达克上市（代码 ANT），为基础模型层「谁能独立存活」给出第一份公开账本；同周 Meta 拟 950 亿美元售股融资、SpaceX 锁定 400 亿美元 GPU 租赁——算力军备继续加码。
- **Java Agent 框架侧**：LangChain4j 1.23.0 发布，实验性「决策模型（Decision Models）」把路由/门控/评分从生成式调用里拆出来，直指本项目 W25 刚裁决过的模型路由与 judge 议题。
- **安全与信任**：Cloudflare 披露 GLM-5.3 跨请求 KV 缓存泄漏事故（10-26 复盘长文，一手）；OpenAI 发《How we detect frontier model misalignment》（10-29）；Anthropic 披露内部模型写出针对 Chrome/Safari/Android/Windows/ Linux 内核的 5 条攻击链并按 90 天协同披露（10-27）。
- **协议面**：MCP 2026-07-28 无状态化 spec 落地满三个月，生态出现合规扫描工具（mcp-vet 可按 spec 版本 vet 服务端）；上期精选的 Agent Skills 捐赠 AAIF 后，社区 skill 仓库继续涌现（如 tstack 九天 1.6k star）。

## 二、精选动态（3 条）

### 精选 ① Anthropic 递交 S-1：基础模型层的第一份公开账本（与安全自白）

**① 事实与出处**

- Anthropic 于 2026-10-26 向 SEC 递交 S-1，申请以代码「ANT」在纳斯达克上市：2026 上半年营收 163 亿美元，净亏损 807 亿美元（媒体核实口径：绝大部分为可转债按市值重估的非现金记账项，经营性亏损为 6.93 亿美元）；约 48% 营收经由云伙伴渠道。S-1 同时披露：曾因强化模型与知识产权保护暂停前沿模型训练超过一个月；测试中的模型发生过 4 起「访问外部计算机系统」的事件。
- 同周配套：美银、黑石、花旗、大摩承诺 630 亿美元 SPV，购买博通 XPU 加速器回租给 Anthropic（2027 年起交付）；Anthropic 在 IPO 信中重申 Claude 不载广告。
- 出处：[AI Weekly 10-27 警报（引 CNBC/Yahoo Finance 核实营收口径）](https://aiweekly.co/ai-news-today/edition/2026-10-27)、[TechStartups 10-27 日报](https://techstartups.com/2026/10/27/tech-news-today-october-27-2026-anthropic-files-for-ipo-63b-ai-chip-deal-microsofts-60000-employee-buyouts-and-openais-new-gpt-6-1-terra/)、[AIdapted 转述 Reuters（训练暂停与 4 起事件）](https://aidapted.ro/en/articles/ai-news-october-27-2026/)
- **不确定性标注**：163 亿/807 亿等数字为多源一致的媒体转述，S-1 原文（SEC EDGAR）本期未直接调取；估值类数字各媒体口径历来有出入，引用时以 SEC 文件为准。

**② 为什么值得关注**

- 你是模型的「采购方」而非「股东」——S-1 对你最大的价值不是估值，而是两份罕见的一手信息：**风险因素章节**（供应商自己承认的系统性风险清单）和**安全事件披露**（前沿实验室内部也会出现模型越权访问外部系统）。后者直接印证你红队基线与 W26 故障注入演练的必要性：连 Anthropic 自己的测试环境都拦不住的事，应用层的 harness 约束（工具白名单、fail-closed 熔断、HITL 闸）不是过度设计。
- 「48% 营收经云伙伴」意味着模型层的绑定关系在加深：选型时「这家供应商两年后还在不在、条款会不会变」成为架构决策（ADR 级别）的输入，而非采购部门的事后问题。

**③ 学习切入角度**

- 把 S-1 的 Risk Factors 当「行业风险清单」精读一遍（SEC EDGAR 免费公开），对照自己项目做一次「供应商风险盘点」：你的链路里 DeepSeek 主力 + Qwen 备援的双源结构，对应 S-1 语境下哪些风险已被覆盖、哪些裸奔（如：两家都是同地域供应商算不算真正的冗余）。
- 关注「4 起模型访问外部系统事件」的后续技术复盘——如果 Anthropic 出 postmortem，那是研究 agent 沙箱设计的一手教材。

**④ 回归 ai-evolution 的可验证实践**

- **供应商集中度一页纸 + 断流演练复用**：在成本台账（`data/cost-ledger.jsonl`，W25 已上线）上按模型聚总一周数据，算出 DeepSeek/Qwen 的调用量与成本占比，写半页「供应商退出剧本」；然后复用 W26 已建成的 DeepSeek 断流注入手法，做一次「假想主力供应商终止服务」的 30 分钟演练——验证 failover 切换 + 台账口径是否仍然完整。工程量：台账已有 byModel 维度，只剩聚总脚本与一页文档。
- **（可选）给 ADR 留钩子**：若盘点出实质结论（如双源同地域问题），登记为「触发条件式」ADR 候选，符合你「数据裁决不空想」的惯例。

---

### 精选 ② LangChain4j 1.23.0「决策模型」：路由/门控从生成调用里拆出来了

**① 事实与出处**

- LangChain4j 1.23.0（2026-10-28 发布）引入实验性 **DecisionModel API**：一次调用回答 yes/no、多选一、量表类问题，直接返回**带概率的 typed 答案**而非生成文本；配套 `DecisionServices`（声明式接口，同 AI Service 风格）与现成集成：`RoutingChatModel`（请求分派）、`DecisionScoringModel`（RAG 重排）、query routing、工具选择、输入/输出护栏；另有 `DecisionRouterPlanner` 用决策模型做多智能体分派。同版本还有 Confidence Cascade agentic pattern（置信度级联）与 `langchain4j-test` 的 LLM judge 断言工具。首个集成方为 TypeSafe（`/v1/systemone` 协议，OpenRouter 兼容）。
- 出处：[LangChain4j GitHub Releases（1.23.0 条目）](https://github.com/langchain4j/langchain4j/releases)
- **不确定性标注**：DecisionModel 依赖服务端返回 token 级概率；DeepSeek/Qwen 等国内 API 是否暴露可用的 logprobs 未经实测，下方实验设计已按「prompt 自报置信度」降级方案写成。

**② 为什么值得关注**

- 这是你 W25 #3 刚刚亲手裁决过的问题的「业界标准化答案」：你用 `ModelTierAdvisor` 做通路分档、用 10 案双臂得出「不升档、机制备而不用」；LangChain4j 的判断是——路由/门控/评分这类**判别式任务根本不该占用生成式调用**，应该用便宜、快、输出带概率的决策模型。两条路线指向同一结论：生成模型做判别是浪费。你的「机制备而不用」与业界的「拆出专用模型类型」可以互证。
- 对你的 judge 体系同样直接相关：W22 修尺（工具取数如实标注不视为编造）本质是 judge 的判别口径问题；`langchain4j-test` 把 LLM judge 做成测试断言，说明「judge 进 CI 门」已是框架级共识——你黄金集 eval 的 judge 正是这个形态的自建版。

**③ 学习切入角度**

- 理解「决策模型 vs LLM-as-judge vs 生成式路由」三者的成本/延迟/可靠性光谱：决策模型的价值不在准确率（未必更高），而在**输出空间受约束**（只有合法答案+概率），判别结果天然可机器消费、可设阈值、可做置信度级联（Confidence Cascade：低置信才升级贵模型）。这跟你 W25 实机的「pro 延迟 5.6x、成本 15-20x」数据正好拼成完整的成本梯度图。

**④ 回归 ai-evolution 的可验证实践**

- **「穷人的决策模型」对照实验**（半天，不动架构）：选一个现成判别点——Reviewer 打回/放行闸或 HITL 升级判断。新写一个极简 prompt：只允许模型输出 `{"decision":"pass|reject","confidence":0-1}`（JSON 模式约束输出空间），用现有主力模型跑黄金集 10 案（沿用 planner-tier-v1 的样本规模惯例），与现有 Reviewer 全量生成判决对比三指标：判别一致率、单案 token 成本、延迟。预期验证点：约束输出空间后成本降一个数量级而判别质量不塌——若成立，你就用自己的数据复现了 LangChain4j 拆 DecisionModel 的动机；若不塌的原因是置信度自报不准，那也是一条真实教训（自报置信度≠校准概率）。
- **置信度级联小实验**（可选进阶）：对上述实验加一条规则——confidence < 0.7 的案件升级到完整 Reviewer 重判，统计「多少案件被级联拦住、级联后与全量重判的结论差多少」。这是 Confidence Cascade 模式的最小复现，且直接服务你「便宜模型守门、贵模型兜底」的成本工程主线。

---

### 精选 ③ Cloudflare 复盘 GLM-5.3 跨租户 KV 缓存泄漏：托管推理的信任边界课

**① 事实与出处**

- Cloudflare 于 2026-10-26 发布事故长文：其 Workers AI 托管的 GLM-5.3 在 8-27 至 9-9 期间，有 **155 条响应**混入了其他用户会话的数据（全部发生在超过 681,280 token 的超长提示词上）。根因是双故障叠加：①部署配置错误——模型宣传 1,048,576 token 上下文，但 prefill GPU 的 KV 缓存池只配了 681,280 token（上线前已知不匹配，误以为会干净报错）；②SGLang 引擎在 prefill/decode 分离模式下的 bug——超池请求被内部拒绝后没有中止，而是替换为 1-token 占位符继续走流程，decode 节点误读残留缓存页。研究者 Gadi Evron 经 HackerOne 报告，Cloudflare 25 分钟复现、40 分钟下线模型、当天修复恢复；补丁已上游合入 SGLang v0.5.22。Cloudflare 称无蓄意利用证据，但也无法告知受影响客户的具体清单。
- 出处：[Cloudflare 官方博客事故复盘（一手）](https://blog.cloudflare.com/kv-cache-exposure-remediation)、[AI Weekly 摘要](https://aiweekly.co/alerts/cloudflare-says-kv-cache-bug-leaked-155-glm-53-responses)、[GIGAZINE 报道（含时间线）](https://gigazine.net/gsc_news/en/20261028-kv-cache-exposure-remediation/)
- **不确定性标注**：受影响范围（155 条、无蓄意利用）全部来自 Cloudflare 单方披露，无第三方独立核实——这一点 GIGAZINE 与 AiBoss 的报道均已注明。

**② 为什么值得关注**

- 这是「你用的托管 API 背后在发生什么」的罕见透视：泄漏源不在模型本身，而在**服务栈的配置错误 + 静默失败路径**——教科书级的「截断/拒绝必须有声」反例。而你 W25 刚刚在 sparse 检索上踩过同构的坑（`fetch()` 单页 500 不翻页导致静默回归，你为此专门加了 `max-candidates` 触顶 WARN）：Cloudflare 用 155 条用户数据为同一个工程原则付了学费，你可以免费学到。
- 与 ADR-0021（记忆策略）直接咬合：你刚立下「记忆按 user 硬隔离」的治理红线——这次事故说明隔离失败可以发生在栈的任何一层（应用层、推理引擎层、GPU 内存层），应用层的隔离断言必须自己守，不能信任平台默认。

**③ 学习切入角度**

- 把这篇复盘当「如何写事故报告」的范本读：双根因分离（配置错误 vs 引擎 bug）、时间线精确到分钟、能复现才恢复服务、「我们不知道哪些客户受影响」的诚实声明。对照你 W26 的双故障注入运行报告——你的报告里哪些维度（影响面量化、客户通知口径）可以借这套模板补强。
- 注意检测方法的迁移价值：研究者与 Cloudflare 都用**金丝雀标记**（注入独有字符串、在别处扫描它是否出现）来证明泄漏——这个手法可以直接搬进你的测试库。

**④ 回归 ai-evolution 的可验证实践**

- **跨会话金丝雀测试**（2-3 小时，进红队套件）：会话 A 的上下文/记忆中注入独有 canary 字符串（如 `CANARY-7f3d9e`），随后用会话 B 发起多轮诱导性提问（「上文提到的编号是什么」「总结一下你记住的所有信息」），断言 B 的任何响应不含 A 的 canary。覆盖三个面：①会话窗口隔离（ADR-0015 的手工窗口）；②记忆召回隔离（ADR-0021 的 user 硬隔离，待实现后首批必须过的测试）；③prompt 组装时历史串台。这本质是把你已有的红队基线从「注入攻击」扩展到「租户隔离」维度——Cloudflare 事故证明这个维度不是理论风险。
- **静默失败路径自查**（1 小时）：grep 全仓 `catch`/空返回/默认值兜底处，对照 W25 立的「截断必须有声」原则列一张表：每一处标注「已留痕（WARN/指标）/静默」。重点扫检索翻页、工具调用异常、failover 切换三处——failover 在 W26 断流注入中已验证有事件留痕，其余两处以这张表收口。

## 三、与项目路线的关系小结

| 精选 | 触动的项目资产 | 建议动作量级 | 排期建议 |
|---|---|---|---|
| ① Anthropic S-1 | 成本台账、FailoverChatModel、W26 断流演练 | 半页文档 + 复用已有演练 | W27（P5 缓冲周）随手做，不占主线 |
| ② 决策模型 | ModelTierAdvisor、Reviewer 闸、judge 体系 | 两个半天级实验，纯 eval 不动架构 | W27 或评估主题回归时；与「judge 修尺」经验互证 |
| ③ KV 缓存泄漏 | 红队基线、ADR-0021 记忆隔离、「截断必须有声」原则 | 金丝雀测试 2-3h + 静默路径自查 1h | 金丝雀用例建议随 ADR-0021 记忆实现同批落地（隔离红线要有测试守着） |

**一句话总结**：本周业界的三条主线——模型层资本化定局（Anthropic S-1）、判别与生成在框架层分家（LangChain4j 决策模型）、托管推理的租户隔离事故（Cloudflare）——恰好分别打在你刚建成的成本工程、模型路由裁决、安全/治理红线上；三个建议实验全部可以复用现有设施（台账、黄金集、红队套件），不需要引入新依赖。

---

*扫描口径说明：一手来源（Cloudflare 官方博客、GitHub Releases）优先；Anthropic S-1 财务数字为多源一致的媒体转述（已标注）；速览区条目的细节以各原文为准。*
