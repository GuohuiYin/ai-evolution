# 每周 AI 业界扫描 · 2026-10-26

> 扫描日期：2026-10-26（周一 14:00，定时任务按计划自动触发——10-20 补扫后本周恢复正常 cadence）
> 信息源优先级：一手（AAIF 官网公告 / PRNewswire 新闻稿）> 权威媒体（财新/澎湃）> 工程媒体（dev.to/CSDN）；厂商自述数据已标注。
> 当前项目坐标：W22 收官中（三角色管线 Planner/Builder/Reviewer 经 Temporal 贯通并切为默认引擎，57 案双臂验收 pipeline 5.44 vs inline 5.11；Skill 机制实测降幅 2.0% 登记止步；judge accuracy 修尺"工具取数如实标注不视为编造"）；**W23 进行中**：HITL 断点接通、检索主体多样性修复（配额重排+锚定补召已落）、Skill 扩列 4 个 + 双臂重测（降幅 9.7% 守线）、**A2A 认知**。ADR-0019（Temporal 可观测分层）本周留痕。
> 上期（10-20）扫描后续：动态二预警的"2.0.1 硬塞 60 秒超时"已列入排查视野；动态一 kappa 校准口径待 W20 #2 校准重生时采用情况见周记。

## 本周全景速览（未精选但值得知道）

- **资本周**：Anthropic 被曝最快本周五向 SEC 公开提交 S-1（H 轮后估值 9650 亿美元；Q3 营收 168 亿美元、环比 +45% 但增速较 Q2 放缓，部分投资者预期 IPO 估值或降至 1.5 万亿美元以下——两个口径并存，均为媒体援引知情人士，未经官方确认）；OpenAI 同步推进约 300 亿美元融资。[CSDN AI 周报](https://blog.csdn.net/yifan99/article/details/168978619 "citation")，[AIHOT 周报](https://aihot.news/weekly "citation")
- **模型层**：月之暗面发布 Kimi K3.1 Pro（约 3 万亿参数，盲测智能指数全球第 4，承诺 11 月 4 日前放出完整权重——媒体转述口径）；DeepSeek 开源内部推理框架 DeepInfer（10-20，兼容英伟达 GPU 与昇腾 950，披露单集群日均处理 155 万亿 token）；DeepSeek V4.1-Flash 更新至 1017 版本（基准提升、输出长度缩短=更便宜更快）——**DeepSeek 是本项目对话模型供应商，其推理成本下探直接利好 token 账本**。[CSDN AI 周报](https://blog.csdn.net/yifan99/article/details/168978619 "citation")，[财新 GPT 周报](https://www.caixin.com/2026-10-23/102496505.html "citation")
- **主动智能体（Proactive Agent）正面交锋**：Anthropic 推出 Claude Duet（10-21，基于 Opus 5.5，后台运行、主动推进任务、每日简报），与 OpenAI Dots、Meta Muse 对位——"被动问答"转"主动代办"成为产品主叙事。[财新 GPT 周报](https://www.caixin.com/2026-10-23/102496505.html "citation")
- **分发渠道**：OpenAI 模型将登陆谷歌云 Model Garden（CNBC 10-21），至此覆盖 Azure/AWS/谷歌云三大平台——多供应商中立分发成为常态。[财新 GPT 周报](https://www.caixin.com/2026-10-23/102496505.html "citation")

---

## 精选动态一：Agent Skills 捐赠 AAIF 成为中立开放标准——我们刚实测过的机制有了行业规范

### ① 事实与出处

- **10 月 22 日（AGNTCon + MCPCon 北美大会，圣何塞），Agentic AI Foundation 宣布接纳四个新项目**：Agent Skills（Anthropic 捐赠）、Open Responses（OpenAI，基于 Responses API 的多供应商模型接口规范）、OpenEnv（Hugging Face，RL 环境）、OpenSandbox（阿里巴巴，agent 沙箱）。AAIF 托管项目从 3 个增至 10 个（既有 MCP、A2A、AGENTS.md、goose、agentgateway、Agent Router）。[AAIF 官网公告](https://aaif.io/news/agentic-ai-foundation-announces-four-new-projects "citation")，[PRNewswire 新闻稿](https://www.prnewswire.com/news-releases/agentic-ai-foundation-announces-four-new-projects-agent-skills-open-responses-openenv-and-opensandbox-302916689.html "citation")
- Agent Skills 捐赠范围：名称与商标、GitHub 组织、agentskills.io 域名；由 Anthropic/Google/OpenAI 等工程师以个人身份维护，MCP 联合作者 David Soria Parra 与 Mahesh Murag 任 Lead Maintainer；拟定路线图首三项：**版本化发布、正式提案流程、skill 编写与安装的安全指南**。[DEV Community（转述 Anthropic 公告）](https://dev.to/976905690/anthropic-donates-agent-skills-standard-to-linux-foundation-agentic-ai-foundation-5cbg "citation")
- 格式本质：每个 skill 是一个文件夹，`SKILL.md` 承载名称/描述/指令，外加可选脚本与参考文件，agent 按需加载（渐进披露）。厂商自述数据（标注）：70+ 平台已集成（ChatGPT、Cursor、GitHub Copilot、VS Code 等），已发布 100 万个 skill、安装量近 2.8 亿。[AgentOps 指南（含 GitHub 实测星数）](https://agentopsguide.com/aaif-agent-skills-open-responses/ "citation")
- 同期细节（对 Open Responses 泼冷水，标注为单一来源实测）：120 个 OpenAI 兼容供应商中仅 21.7% 声明 `/responses` 端点，Anthropic 不在支持方之列——统一接口仍是目标而非事实。[daily.dev 聚合](https://daily.dev/posts/openai-and-anthropic-donate-open-responses-and-agent-skills-to-the-agentic-ai-foundation-g0bsrnlul "citation")

### ② 为什么值得关注

我们上周刚走完 Skill 机制的完整工程循环：W22 #2 分红口径 skill + SkillRegistry + 三档注入，实测 token 降幅 2.0% <20% 守线→"登记止步不硬上"；W23 #4a 扩列至 4 个、#4b 双臂重测 9.7% 守线。**这个我们判定"当前规模下收益不够"的机制，本周成为中立治理的行业标准**——这不推翻我们的数据裁决（裁决基于本工程语料与流量形态），但改变了资产的长期属性：skill 从"自家 prompt 资产的一种组织形式"变成"可跨 70+ 平台携带的格式"。我们的 4 个 skill 若按标准格式组织，未来可零成本带出本仓库（面试作品集、跨工具复用）。

### ③ 学习切入角度

读 agentskills.io 规范原文，重点学两个设计：① **渐进披露的三层加载**（元数据常驻 → 触发时读指令 → 需要时才读脚本/资源）——这是"context 是稀缺资源"原则的标准化实现，与我们 W22 实测的 token 成本问题同根；② **skill = 指令 + 脚本 + 资源的打包边界**——对比我们 skill 目前"纯指令注入"的形态，理解什么场景需要带脚本（年报横读 skill 若带一个 Python 计算脚本，就是规范内的合法形态，呼应 ADR-0018 语言策略）。

### ④ 回归 ai-evolution 的可验证实践

1. **格式对照表**（文档级，30 分钟）：读规范后把 4 个在库 skill 与 SKILL.md 标准字段做映射（name/description/触发条件/指令体），差异登记进 `docs/notes/`——只对照不迁移，是否对齐标准由 Owner 裁决。
2. **若决定向标准靠拢**：复用 W22 的 token 实测 harness 重跑一次双臂对照，验证"格式标准化不破坏 9.7% 降幅守线"——把格式变更也纳入数据裁决纪律（ADR-0007 机制的延伸）。
3. **skill 来源策略明文化**：AAIF 路线图把"skill 编写与安装的安全指南"列为前三项（对照本周 GhostAction 事件，见动态三——skill 市场已被投毒过）；我们目前 skill 全部内部 authoring 是正确默认，写一句话进造数规范或 AGENTS.md 挂账即可：**第三方 skill 入库前必须过红队审查**。
4. **Open Responses 观察登记**：我们双供应商切换（DeepSeek/Qwen）走 OpenAI 兼容协议，若 Open Responses 普及，failover 的协议差异面将缩小——登记能力地图观察项，不动作。

## 精选动态二：A2A 协议现状盘点——W23"A2A 认知"任务的现成底稿

### ① 事实与出处

- **A2A（Agent2Agent）已达生产稳定**：v1.0.0 于 2026-03-12 发布（首个 production-ready 版本），v1.0.1 于 2026-05-28；2026 年 8 月被 AAIF 正式接纳为 Growth Stage 项目。v1.0 引入 **JWS 签名 Agent Card**（域名验证）、gRPC 与 JSON-RPC 2.0 / HTTP+REST 三种绑定并存、OAuth 2.0 Device Code + PKCE、ISO 8601 毫秒级时间戳。技术指导委员会含 AWS/Google/IBM/微软/Salesforce/SAP/ServiceNow，无单一厂商可独改 wire format。[AI Growth Agent 综述（2026-10-19 更新）](https://aigrowthagent.co/articles/a2a-protocol-explained-2026/ "citation")，[bestmultiagent 框架追踪（引 GitHub releases）](https://bestmultiagent.com/ "citation")
- **分工共识已成形**：MCP 管垂直（agent→工具/数据），A2A 管水平（agent→agent 委托），两者互补不竞争；A2A 刻意保持 agent 不透明（"不共享内部记忆、工具或私有逻辑"）。生态对照（2026-10-20 实测各厂商文档）：代码框架与云运行时（LangGraph/CrewAI/Bedrock AgentCore/Gemini ADK）普遍双语种（MCP+A2A），工作流平台以 MCP 为主。[Taskade 平台对照](https://www.taskade.com/blog/ai-agent-platforms "citation")
- 配套动向：Agent Payments Protocol（AP2）把 A2A 推向受监管交易；Sierra 牵头的 Personal Agent Protocol（PAP，与 Meta/Shopify/Stripe/沃尔玛合作）仍在 v0.1 前的空窗期，A2A Agent Registry 也未发布——**agent 间的发现与信任基础设施仍是半成品**。[AI Agent Store 日报](https://aiagentstore.ai/ai-agent-news/2026-october "citation")

### ② 为什么值得关注

W23 计划里明确列了"A2A 认知"任务，本周材料恰好给出完整答案，包括一个对我们**同等重要的反向结论**："单进程、单团队系统应跳过 A2A"——我们 W22 的 Planner/Builder/Reviewer 三角色管线选择了进程内 Temporal 编排（打回批注回流、fail-closed），而不是多 agent 分布式互操作，A2A 社区的"when to skip"共识为这个选择提供了外部判据：**角色分工 ≠ 进程边界 ≠ 组织边界，只有最后一种才需要 A2A**。同时 MCP（我们 W6/W12 已双向贯通）与 A2A 的互补定位，让协议版图完整了：L2 学 MCP 是"工具接入"，L4 之后若有多系统协作，A2A 是"同事接入"。

### ③ 学习切入角度

读规范时带着自己刚建的三角色管线去读：A2A 的九个 task 生命周期状态 vs 我们 plan→build→review→打回 的状态机——你会发现我们的 Reviewer 打回循环在 A2A 语境下对应 `input-required` 状态的跨 agent 版本。理解"为什么 A2A 要让 agent 保持不透明"（组织间信任边界 > 调试便利），与我们 ADR-0015"循环必须显式可观测"是不同边界上的不同正确解。

### ④ 回归 ai-evolution 的可验证实践

1. **认知笔记半页**（W23 任务直接产出，`docs/notes/a2a.md`）：三句话版图（MCP 垂直/A2A 水平/何时跳过）+ 我们三角色管线为什么不用 A2A 的论证 + 一个触发条件（"若未来接入外部研究 agent 或向合作方暴露研究能力，重评 A2A"）。
2. **Agent Card 思想实验**（半小时文档练习）：假设把个股研究 agent 对外暴露，起草它的 Agent Card——skills 描述怎么写、三条红线如何表达为策略声明、数据溯源口径（tushare+时点）如何放进能力描述。这个练习实质是"用协议视角审视自家系统的对外契约"，产物可进作品集。
3. **Java 生态实测登记**：查 Spring AI / LangChain4j 的 A2A 支持现状（预计不成熟），结果登记能力地图——若确实缺位，这是"Java 侧等生态"的又一实证（呼应 ADR-0018 与上期 MCP 2026-07-28 的 Java 跟进延迟）。

## 精选动态三：GhostAction 第二阶段——"克隆即执行"：供应链攻击与提示注入合流

### ① 事实与出处

2026 年 10 月第 4 周网络安全周报披露 GhostAction 攻击两阶段（安全媒体整理，具体数字为攻击面口径非实际失陷数，标注）：

- **第一阶段（10/8–12）**：攻击者用约 1,900 个被盗 GitHub 账号向 30,000+ 公开仓库推送伪装成"安全审计"的 Actions workflow，试图提取 3,174 个 CI 密钥（AWS/GitHub/OpenAI/Anthropic 等）；GitHub 10/9 开始阻断。
- **第二阶段（10/14–15）**：转换打法——在 94 个仓库植入伪装成 Font Awesome 字体文件的 38 行 Node.js 载荷，通过 `.vscode/tasks.json` 中名为 `eslint-check` 的任务在**开发者用 VS Code / Cursor 打开项目时自动执行**；载荷递归搜刮主目录的 AWS 密钥与各类 token、安装 cron 持久化，并**内嵌一条给 Cursor 编码智能体的提示注入指令，要求 agent 执行该文件**。[CSDN 网络安全周报](https://blog.csdn.net/weixin_41905135/article/details/168868781 "citation")
- 背景旁证（厂商博客，标注来源属性）：2026 年 1 月 ClawHavoc 事件中某 agent 技能市场首轮审计即发现 341 个恶意 skill（后增至 1,184 个）——"agent 的能力包/工具链"已是 established 攻击面。[Reflectiz 年度攻击盘点](https://www.reflectiz.com/blog/the-7-biggest-ai-agent-attacks-of-2026/ "citation")

### ② 为什么值得关注

这条与我们有三层直接关系：① **本仓库就是 GitHub 托管 + GitHub Actions CI + 开发者本机用 AI IDE 打开**——GhostAction 的两个攻击面（恶意 workflow 偷 CI secrets、IDE 任务自动执行）我们全都具备；② 攻击载荷**内嵌给编码 agent 的提示注入**——"内容可以指挥 agent"从我们红队基线的测试用例（公告语料藏指令）变成了在野攻击的标准组件，间接注入不再是学术话题；③ 与动态一呼应：AAIF 把 skill 安全指南列进路线图前三名不是巧合，能力包市场已被实际投毒。

### ③ 学习切入角度

把自家红队基线的威胁模型从"应用运行时"扩展到"开发时"：我们的红队覆盖 /ai/chat 与 /mcp 两个运行时面，但开发面（IDE 打开仓库、CI 跑 workflow、`git clone` 第三方代码）同样是 agent 时代的攻击面。架构师视角的完整威胁模型 = 运行时 × 开发时 × 供应链。

### ④ 回归 ai-evolution 的可验证实践

1. **仓库自检**（10 分钟）：检查本仓有无 `.vscode/tasks.json`（尤其 `runOnFolderOpen` 类自动触发）、`.idea/` 共享运行配置；确认无自动执行项或内容为已知白名单。
2. **CI 卫生核查**：过一遍 `.github/workflows/`——Action 是否 pin 到 commit SHA 而非 tag；有无 `pull_request_target` 等危险触发器；secrets 是否只在必要 job 暴露。GhostAction 一期正是利用"伪装安全审计 workflow"偷 secrets，本仓 CI 每周跑 eval 用真 API key，值得核一遍。
3. **红队文档增补一类**（纯文档）：`docs/security/` 补"开发时/工具链侧注入"类别——载荷经项目文件指挥编码 agent，与运行时语料注入同类不同面；防御注记：编码 agent 的工具确认对话框不要无脑点通过（呼应 Claude Code 解剖的"93% 批准率=审批疲劳"案例）。

---

## 与项目路线的关系小结

| 精选 | 映射当前工作 | 本周可动作 |
|---|---|---|
| Agent Skills 入 AAIF（开放标准） | W22 #2 / W23 #4 Skill 机制 | 格式对照表（只对照不迁移）+ skill 来源策略一句话挂账 |
| A2A v1.0 稳定 + 版图分工 | W23 "A2A 认知"任务 | `docs/notes/a2a.md` 半页 + Agent Card 思想实验 |
| GhostAction 供应链+注入合流 | 红队体系扩展 + 仓库卫生 | IDE/CI 自检 10 分钟 + 红队增"开发时"类别 |

本周主线一句话：**agent 技术栈的"管道层"正在向中立治理快速归拢**（AAIF 一年 3→10 个项目：MCP/A2A/Skills/Responses/沙箱），而攻击者同步把目标从运行时扩展到开发时与供应链。对我们的含义很直白：在库资产（skill、MCP 工具、AGENTS.md）按行业标准组织就是可携带资产；同时威胁模型要长出"开发时"这一面。W23 按既定计划推进即可——A2A 认知任务的底稿本期已备好。
