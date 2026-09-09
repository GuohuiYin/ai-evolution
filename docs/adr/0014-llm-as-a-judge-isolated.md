# ADR-0014：生成层评估用 LLM-as-a-Judge，judge 与被测通路物理隔离

## 状态

已接受（W8-4，2026-09-09）

## 背景

W8 要把 eval 从检索 Recall 扩展到三维（准确性/遵循度/溯源完整性）。自由文本答案的对错、
拒答得当与否是语义判断，字符串匹配/规则判不了，必须引入 LLM-as-a-Judge。
风险：judge 设计稍有不当（共用业务 advisor、评分口径漂移、黑盒评黑盒）整个评估体系失真。

## 决策

1. **judge 与被测通路隔离**：`LlmEvalJudge` 用裸 `ChatClient`（仅 ChatModel 构建），
   不继承业务 advisor 链（token 记账、prompt 日志、红线切面都不污染评估）
2. **rubric 三维各 0-2 分**（`prompts/eval-judge-v1.md` 版本化资产）；关键规则——
   **拒答类用例 grounding 直接满分**（拒答无需溯源，不倒逼乱引来源）
3. **judge 自身被评估**：每轮跑批抽 5 条人工锚定，判定不一致先修 rubric 再信分数
   （W8 首轮锚定 5/5）
4. **评估入口=生产入口**：被测回答必须来自 `ChatService` 统一入口（含路由），不建旁路；
   依赖方向已在 ArchUnit 白名单显式登记（eval→chat/prompt）
5. **judge 输出不可解析一律 fail-fast**，不兜底打分（评估事故必须可见）

## 替代方案与否决理由

| 方案 | 否决理由 |
|---|---|
| 规则/正则判生成质量 | 自由文本等价表述太多，只能判格式判不了语义 |
| judge 复用业务 ChatClient | 评估被业务横切污染，分数不可复现 |
| 跳过 judge 锚定 | 用一个未校准的黑盒评另一个黑盒 |

## 后果

- prompt/检索改动有了三维裁决器：few-shot A/B 实验（49:49 打平不启用）即由此裁决
- 每次 prompt 演进必须先跑生成 eval——eval-first 从检索层扩展到生成层
- 成本已知且低廉：一轮 16 条约 ¥0.17（见 docs/cost/w8-cost-report.md）
