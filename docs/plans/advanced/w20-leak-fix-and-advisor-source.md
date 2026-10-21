# W20 计划提案：评估基建修复 + Advisor 源码精读（待 Owner 裁决）

> 2026-10-20 提案。蓝图 W19-20 行（专项一·源码阅读 + Temporal）经 W19 裁决①顺延后的执行周；
> 同时承接 W19 首演产出的最高优先级挂账——Thought 泄漏专项与 judge 校准重生。

## 背景（为什么这个顺序）

1. **judge 可信度破产是评估体系的动脉伤**：W19 校准复测 36.7% 不达标，主病灶是 judge 对
   Thought 泄漏 preamble 系统性偏松。缺陷不修复+校准不重生，后续一切 eval 判读都没有
   可信刻度——**修复必须排在源码阅读前面**。
2. **泄漏根因已定位到代码行**（W19 #4 双机制拆解）：协议违背降级
   （`DeepSeekResearchModel` 兜底 `Final("", raw)`，六批日志 7 次 WARN，全部中文"思考："
   开场）+ MAX_STEPS 触顶后的提取假象。修复面清晰、边界可控。
3. **专项一已顺延一次，不能再滑**：ChatClient"会造"是 §3A L1 唯一 ⚠️ 项，
   蓝图验收标准明确（不看文档能答"加一层自定义越界拦截，钩子在哪"）。
4. **Temporal 同源三件套（替换状态机/会话记忆 L2/可观测 ADR）一周装不下**：
   按 W19 裁决①既定口径（完成定义后滑，W24-27 缓冲吸收）处理，见裁决②。

## 任务分解表

| # | 任务 | 验收条件 | 状态 |
|---|---|---|---|
| 1 | **Thought 泄漏专项修复**（TDD）：① `ReactProtocolParser` 中文标记容错（思考/行动/行动输入/最终答案 + 既有全角冒号、加粗容差）；② ReAct 系统 prompt 协议纪律强化（英文标记强制 + 反例 few-shot，**judge prompt 不动**）；③ MAX_STEPS 触顶追加**无工具合成步**（裁决①B，合成失败退兜底文案）；④ `DeepSeekResearchModel` 兜底路径加协议违背计数指标（tool-audit 先例留痕） | parser 单测覆盖中文/混排/截断漂移现场（7 次 WARN 原文做成测试夹具）；泄漏 6 案复跑最终答案零 preamble | ✅ 2026-10-21：`1332939` parser 容错 + `0caa1a0` 方案 B protocol 子包 + `78120f0` prompt v2 + `8cabfb1` 合成步 + `92d87a2` 违背计数；6 案复跑零 preamble（[验证实录](../../eval/generation/w20-leak-fix-verification.md)），eval 两批总体 5.1/6 零回归 |
| 2 | **校准重生 + 提取修正 + 规范第五条**：① 工作表提取脚本识别 `stopReason=MAX_STEPS`，以 `LoopResult` 兜底文案为准（假象源消除）；② 修复后重跑校准抽测（同分层同种子口径，守线 ≥80%）；③ 造数规范增补第五条"校准复测触发条件"（语料轮换/在案缺陷状态变化/judge 相关配置变更后必测） | 校准复测达标登记；规范第五条入 [golden-set-authoring](../../eval/golden-set-authoring.md) + changelog | ✅ 2026-10-21：①③ `3e34098`（提取口径改为 eval 报告「回答：」块 + 规范第五条 + changelog）；② 复测揭盲 **63.3%（19/30）不达标，按 W19 先例立案**——[w20-calibration-rerun](../../eval/generation/w20-calibration-rerun.md)：方向反转（judge 偏松→偏严 9:2），三新病灶=时效先验误判（#8 确诊：2026 半年报 M26 在库，judge 误判"不存在"）/ rubric 维度误用（#6 compliance）/ 人工漏判正面案例（#3 judge 抓到 238.82 张冠李戴）；处置候选已登记，judge prompt 是否解禁待 Owner 裁决 |
| 3 | **Advisor 源码精读**（专项一主菜）：Spring AI ChatClient → Advisor 链调用顺序、Request/Response 上下文载体、内置 Advisor（RAG/记忆/日志）挂载点；产出 **Advisor 链结构图**（落 `docs/arch/`）+ 30 分钟分享稿 + 钩子自测问答 | 蓝图验收：不看文档答出"加一层自定义越界拦截，钩子在哪"；结构图 Owner 过目 | ✅ 2026-10-21 `12fcd43`：[结构图+挂载点+钩子自测](../../arch/advisor-chain.md) + [30 分钟分享稿](../../notes/advisor-chain-talk.md)；钩子标准答案=直接实现 CallAdvisor 短路（BaseAdvisor 无短路出口），ChatClientCustomizer 全局织入 |
| 4 | **Temporal 三件套**（替换状态机 / 会话记忆 L2 持久化 / 可观测 ADR） | **顺延 W21 上半周**（裁决②，完成定义后滑一周，W24-27 缓冲吸收） | ⬜ 顺延 |

## Owner 裁决记录（2026-10-20 拍板，动工前已定）

① **MAX_STEPS 终答处置 = B**：触顶追加**无工具合成步**（已收集 observations 喂回模型
强制出 Final Answer，合成失败再退兜底文案）；合成步 prompt 绑定"仅基于已查证信息，
不足则如实声明"诚实红线，防"硬凑答案"
② **Temporal 三件套 = B**：整包顺延 W21 上半周，完成定义后滑一周，W24-27 缓冲吸收
（W19 裁决①同口径）；会话记忆 L2 持久化与可观测 ADR 同源随包
③ **分享形式 = 文稿**：Advisor 链 30 分钟分享以文稿交付（W13 有 15 页 PPT 先例，
本轮从简）

## 明确不做（防范围蔓延）

- 不动 judge prompt（铁律）；校准复测沿用现有 judge 配置
- W19+ 挂账候选不夹带：runner 逐案增量输出、Qdrant restart/Compose 托管、API 断连
  指数退避、改写层 literal 保真、速览 chunk 加权实验、mock 数据轮换联动——
  均在 [w19-rotation-first-run-review](../../eval/w19-rotation-first-run-review.md)
  第四节登记，择机立项
- 不扩黄金集规模（本周是修复周不是造数周）
- LangChain4j 对照阅读不进本周（蓝图"二选一精读"，泛读清单登记）

## 顺序与节奏

#1 泄漏修复（1-1.5 天，TDD 先行）→ #2 校准重生（0.5-1 天，依赖 #1）→
#3 源码精读（2.5-3 天，块状时间）→ #4 按裁决②。每天 1 项以内，逐项 review；
代码改动 commit 前报变更清单，push 等明确口令（A2 双确认）。
