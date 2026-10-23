# W21 计划提案：Temporal 顺延包 + judge 病灶处置 + L4 完整启动（待 Owner 裁决）

> 2026-10-21 提案。三件事汇流：① W20 裁决②的既定顺延包（Temporal 三件套）；
> ② W20 #2 揭盲立案的后续处置（需裁决 judge prompt 是否解禁）；
> ③ 蓝图 W21-23 行（L4 完整：Sub-Agent 协作 + Skill 渐进披露）按既定节奏启动。

## 背景（为什么这样排）

1. **Temporal 顺延包是既有承诺**：W19 裁决①顺延一次、W20 裁决②再顺半周，
   完成定义已后滑至 W21 上半周，W24-27 缓冲吸收——不能再滑，否则侵蚀缓冲。
2. **judge 新病灶不解，eval 判读持续失真**：W20 揭盲 63.3% 不达标
   （[w20-calibration-rerun](../../eval/generation/w20-calibration-rerun.md)），
   主病灶"时效先验误判"会随每次语料轮换复发。处置候选 a（语料时点清单注入）
   不动 rubric 只动上下文，工程量小，但**触碰 judge prompt 需 Owner 裁决解禁**
   （W20 铁律是修复周隔离变量，使命已完成）。
3. **蓝图 W21-23 行不能整周让位**：L4 完整是三周行程（Sub-Agent 三角色跑通 +
   Skill 加载实测 Token 降幅），W21 不动设计，W22-23 会被迫压缩。

## 任务分解表

| # | 任务 | 验收条件 | 状态 |
|---|---|---|---|
| 1 | **Temporal 三件套**（顺延包，上半周）：① Temporal 到会调最小环——本地 dev server + hello workflow 跑通，弄清 workflow/activity/retry 语义；② **loop run 状态持久化裁决与落地**——现状无 Spring StateMachine 实物（显式 ReAct 循环），对象是 ResearchLoop run 状态（步进/中间产物/断点恢复），替换范围先裁决后动手；③ 会话记忆 L2 持久化——`InMemoryChatMemoryRepository` → Redis/DB 仓储（同源病：重启即失忆、多副本断片）；④ 可观测 ADR——Temporal 引入后的 trace/指标方案入 `docs/adr/` | Temporal 本地跑通有实录；loop run 持久化方案 Owner 过目后落地；记忆 L2 可切换（配置开关，内存版留缺省）；ADR 入库 | ✅（①`d944187` ②`73b62ad` ③`8793ef7`+`105418a` ④`addacf4`） |
| 2 | **judge 病灶处置**（裁决①：解禁限输入侧）：a. judge 输入侧注入语料时点清单（在库文档+时点，消解"2026 半年报不存在"类误判，**rubric 冻结**）；b. 校准工作表参照旁附语料锚点页码（人工侧，#3 漏判教训，与 a 同批）；c. compliance 维边界澄清——**视 a 复测效果再定，默认不做**——动作后按规范第五条复测校准（同分层同种子 10 案，守线 ≥80%） | 处置落地 + 校准复测结果登记（达标收官/不达标再立案）；changelog 留痕 | ✅（`aa7b4d4` 处置落地；复测 60% 不达标立案 [w21-calibration-rerun](../../eval/generation/w21-calibration-rerun.md)——病灶①已除，候选 c 待裁决） |
| 3 | **L4 完整启动·设计先行**（下半周）：Sub-Agent 协作三角色选型与职责切分设计（蓝图 Planner/Builder/Reviewer/Monitor 取三，结合个股研究域）+ Skill 渐进式披露调研（Token 降幅测量方案设计，为 W22 实测铺路） | 设计文档入库（docs/plans/advanced/ 或 docs/arch/）；三角色边界 Owner 过目 | ✅（`e4b4561`，三角色边界+测量守线 Owner 已过目） |

## Owner 裁决记录（2026-10-21 拍板，动工前已定）

① **judge prompt 解禁，但仅限输入侧上下文注入**（候选 a 语料时点清单）；
   rubric/评分尺继续冻结——隔离变量精神延续；候选 b（工作表锚点页码）同步采纳，
   候选 c（compliance 边界澄清）视 a 复测效果再定
② **Temporal 替换对象 = ResearchLoop run 状态**（步进+中间产物+断点恢复），
   无状态机实物的口径确认；仍先跑最小环再落替换设计
③ **L4 本周只做设计文档**——Sub-Agent 三角色选型与 Skill 披露调研成文即可，
   骨架留 W22，时间盒让给 Temporal

## 明确不做（防范围蔓延）

- W19+ 挂账候选继续不夹带（runner 逐案增量输出 / Qdrant Compose 托管 / API 断连退避 /
  改写层 literal 保真 / 速览 chunk 加权 / mock 轮换联动——均已在
  [w19-rotation-first-run-review](../../eval/w19-rotation-first-run-review.md) 登记）
- LangChain4j 对照阅读不进本周（蓝图"二选一精读"已闭环 Spring AI，泛读清单登记）
- 黄金集不扩规模；12987 检索抖动维持 v5 挂账，不并入 judge 处置
- Tushare hosted MCP 体验（capability-map 挂账，候选 W22-23 工具周，本周不动）

## 顺序与节奏

#1 Temporal（上半周，2-2.5 天，先最小环后裁决范围）→ #2 judge 处置（0.5-1 天，
含校准复测）→ #3 L4 设计（下半周，1.5-2 天）。每天 1 项以内，逐项 review；
代码改动 commit 前报变更清单，push 等明确口令（A2 双确认）。

## 观测项（不立项，仅留痕）

- W20 合成步线上零触发（协议纪律后 6 步内收敛）——W21+ 若复现 MAX_STEPS 触顶，
  核对合成质量并回填实证
- 开工自检清单候选二则：nohup PATH 缺 uvx 路径；沙箱 curl 直连 Maven 仓库不通
  （源码获取走 GitHub raw）
