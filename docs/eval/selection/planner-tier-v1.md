# Planner 定档实验 v1：deepseek-v4-flash vs Qwen3.5-35B-A3B

- 日期：2026-10-23
- 目的：为三角色管线的 Planner 角色定档主力模型（W22 #1a，Owner 裁决①）
- Prompt：`src/main/resources/prompts/planner-v1.md`（输出严格 JSON：subQuestions + channel 标注）
- 脚本：`scripts/planner_tier_experiment.py`；样本：`planner-tier-v1-samples.json`
- 分层 10 案：factual ×2 / compound ×3 / multi-turn ×2 / in-domain-unanswerable ×2 / redline ×1

## 一、性能数据

| 指标 | deepseek-v4-flash | Qwen3.5-35B-A3B（思考模式） |
|---|---|---|
| JSON 可解析率 | 10/10 | 10/10 |
| 平均延迟 | **1.77 s** | **129.7 s** |
| 延迟区间 | 1.0–3.2 s | 24–185 s |
| 平均 completion tokens | **171** | **3729**（约 22 倍） |
| 平均 prompt tokens | 377 | 367 |

Qwen 臂为思考模式，绝大部分 token 消耗在推理过程而非输出本身；对"拆子问题 + 标注通路"这类轻量规划任务，思考开销不成比例。

## 二、逐案质量对照（人工评）

| # | 案例 | 分层 | DeepSeek | Qwen | 判定 |
|---|---|---|---|---|---|
| 1 | 茅台 2025 归母净利 | factual | 1 子问题，rag ✅ | 1 子问题，**none** ❌ | Qwen 通路标错：语料内事实标 none = 不查任何通路直接答，幻觉风险，红线级错误 |
| 2 | 宁德 2024 营业总收入 | factual | rag ✅ | rag ✅ | 平 |
| 3 | 茅台 2024 净利+分红 | compound | 拆 2（净利/分红）✅ | **未拆**，合并为 1 条 ❌ | Qwen 违背拆解语义，Builder 将无法分点取证 |
| 4 | 600519 营收+工艺 | compound | 拆 2，且解析 600519→贵州茅台 ✅ | 拆 2 ✅ | DeepSeek 略优 |
| 5 | 宁德归母净利+市占率 | compound | 拆 2 ✅ | 拆 2（市占率年份漂移为"最新"，可接受）✅ | 平 |
| 6 | 中期分红算在里面吗 | multi-turn | 指代消解正确，rag ✅ | 指代消解正确，rag ✅ | 平 |
| 7 | 它的净利润呢 | multi-turn | 消解为 600519 净利 ✅ | 同 ✅ | 平 |
| 8 | 冰淇淋营收占比 | in-domain-unanswerable | rag（查不到再拒，策略正确）✅ | rag ✅ | 平 |
| 9 | 固态电池量产时间 | in-domain-unanswerable | rag ✅ | rag ✅ | 平 |
| 10 | 值得买入吗 | redline | none ✅ | none ✅ | 平 |

质量小结：DeepSeek **10/10 可用**；Qwen **8/10**，含 1 处红线级通路错误（案 1）+ 1 处 compound 未拆（案 3）。

## 三、定档结论

**Planner 主力档定 deepseek-v4-flash。**

1. 质量：DeepSeek 全分层零失误；Qwen 在事实案通路标注上犯红线级错误，compound 拆解不稳。
2. 成本：延迟差约 73 倍（1.77s vs 129.7s），completion token 差约 22 倍；规划步骤在管线上每轮都走，Qwen 思考模式完全不可用。
3. 无 Upsell 必要： Planner 任务为结构化轻规划，当前主力档已满分，不留高档位入口。

## 四、后续注意

- 若未来需要回调 Qwen 系，必须先关思考模式（`enable_thinking: false`）或换非思考模型，否则单调用 2–3 分钟不可接受。
- planner-v1.md 尚未接线（零运行时影响）；本报告为三角色管线 #1b-e 的 Planner 实现依据。
