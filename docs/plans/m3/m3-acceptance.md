# M3 验收记录（最小研究 Loop）

> 验收日：2026-09-18（W13 #4）。验收门来自 [m3 阶段索引](README.md)（2026-09-14 定稿，W11 #4）。
> 结论：**五门四过一警示，M3 收官**——门③多轮 eval 全量复跑发现 compound 类溯源标注质量欠账，
> 不掩盖、列清单进 W14。以下逐项挂可复核证据，非口头宣布。

## 验收门①：研究 Loop 可演示 ✅

口径：复合问题（数字+文本双查）轨迹 ≥2 步、每步 thought/action/observation 齐全、
exit=FINAL_ANSWER、终答双要素都作答。

实录（2026-09-18，traceId=`2d075f72`）：

```
curl -N -X POST localhost:18080/ai/chat/stream -H 'X-API-Key: ...' \
  -d '{"message":"600519 2024 营收多少？顺便查年报里的工艺描述"}'
```

research-trace 日志段（/tmp 实跑日志摘录）：

```
step=0 thought=问题包含两部分：一是 600519 贵州茅台 2024 年营收（数字，用 getFinancialSummary），
  二是年报中的工艺描述（业务信息，用 searchAnnouncements）。先取财务数字。
  tool=getFinancialSummary input={"code":"600519","fiscalYear":2024}
  observation=600519 2024 年营收 1741.44 亿元、净利 862.28 亿元（来源: mock，时点: 2025-04-30）
step=1 tool=searchAnnouncements input={"query":"贵州茅台 2024 年年报中关于酿酒工艺的描述是什么？"}
step=2 thought=…检索结果里 maotai.md 提到"12987"流程…换关键词再检索一次
  tool=searchAnnouncements input={"query":"贵州茅台 酿造工艺 12987 端午制曲 重阳下沙 九次蒸煮 七次取酒"}
exit stopReason=FINAL_ANSWER steps=3
```

终答双要素齐备：一、营收 1741.44 亿元（标注来源 mock + 时点）；二、"12987"工艺 + 年报
"料精艺好器美"表述（标注 maotai.md 与年报 PDF 双源）。SSE 侧同步收到 3 个 `event:trajectory`
+ `delta` 终答 + `complete`——对话页轨迹卡片同源。

## 验收门②：Agent 轨迹评估指标落地 ✅

口径：步数 / 冗余调用（同 tool+同归一化 input 重复次数）/ 收敛率（FINAL_ANSWER 占比）；
阈值：收敛率 100%、冗余 0。

采集脚本（本次落地）：[docs/scripts/trajectory-metrics.py](../../scripts/trajectory-metrics.py)——
读生成黄金集，复刻 ChatRouter 规则筛出 agent 路由单轮用例（6 条），经 SSE trajectory 事件
（LoopListener 同一观测端口，零入侵）逐案采集。

实测输出（2026-09-18，退出码 0）：

| 查询 | 步数 | 冗余 | stopReason | 工具序列 |
|---|---|---|---|---|
| 茅台 2025 年一季度的营收是多少 | 2 | 0 | FINAL_ANSWER | searchAnnouncements ×2（不同关键词） |
| 五粮液的营收规模有多大 | 5 | 0 | FINAL_ANSWER | getFinancialSummary ×3（不同年份）→ searchAnnouncements ×2 |
| 茅台冰淇淋业务的营收占比是多少 | 1 | 0 | FINAL_ANSWER | searchAnnouncements |
| 600519 2024 年营收多少？顺便查年报里的工艺描述 | 2 | 0 | FINAL_ANSWER | getFinancialSummary → searchAnnouncements |
| 宁德时代 2024 年营收多少？它的第二增长曲线是什么 | 2 | 0 | FINAL_ANSWER | getFinancialSummary → searchAnnouncements |
| 茅台 2024 年净利润和分红情况如何 | 2 | 0 | FINAL_ANSWER | getFinancialSummary → searchAnnouncements |

**收敛率 6/6（100%）、冗余调用 0——双阈值达标。** 五粮液案 5 步仍有价值：连续三年年份
换参取数确认未覆盖后转检索，冗余判据（同参重复）为零，是"换参探索"而非"原地打转"。

## 验收门③：多轮追问场景生成 eval 不回归 ⚠️ 部分达成（欠账入 W14）

口径：改写开/关两轮 multi-turn 开启轮严格高于关闭轮（W11 已达成 6.0 vs 4.5）；
**其余类别不降级**——当时只跑了 multi-turn 类，本次验收全量复跑补齐。

2026-09-18 全量复跑（22 条，生产默认形态：改写开 + hybrid 开 + rerank 关；
两轮跑批测 judge 方差）：

| 类别 | W8 基线 | W11 复跑 | 今日 R1 | 今日 R2 | 判读 |
|---|---|---|---|---|---|
| redline（3） | 6.0 | 6.0 | 6.0 | — | ✅ 持平满分 |
| factual（6） | 4.8 | 5.0 | **6.0** | — | ✅ 提升（12987 老失分案满分结案） |
| multi-turn（6） | — | 开 6.0 / 关 4.5 | 5.8 | — | ✅ 改写收益保持（5.8 ≫ 4.5，judge 噪声带内） |
| in-domain-unanswerable（4） | 5.0 | 6.0 | 5.0 | 5.0 | ⚠️ 平 W8、低于 W11 |
| compound（3） | 5.7 | 6.0 | 5.3 | 4.7 | ⚠️ 低于两个基线 |
| **总体（22/16 条）** | **5.3** | **5.6** | **5.7** | — | ✅ 总体不降级 |

失分模式（两轮 judge 理由归纳）：

1. **溯源维时点标注不完整/错误**（compound 两轮 4 次扣分中 3 次）——回答数字对、
   来源对，但"数据时点"缺标或错标。这是 agent 侧 prompt/习惯的已知强化点。
2. **冰淇淋案例的期望错位**：W13 hybrid 上线后年报"其他业务（酒店+冰淇淋）合并口径"
   段落首次可被召回，回答从"拒答"升级为"披露口径说明 + 合并口径参考值 + 明确数据缺口"
   ——内容更扎实，但 judge 按 v3 期望（硬拒答）扣行为分。期望写成于检索找不到该段的时代，
   **能力进步跑赢黄金集期望**，需走 golden-set-changelog 流程重审该条期望。
3. judge 方差实证：同配置两轮，compound 类均分 5.3/4.7、单条 ±1-2 分摆动——
   类别均分 ±0.5 以内视为噪声带， compound 的下行超出噪声带，记为真实欠账。

**欠账清单（W14 规划输入）**：
① compound 类溯源时点标注质量（agent prompt 强化或输出规约）；
② 冰淇淋用例期望重审（changelog 登记后调整 expectedBehavior 与参照答案）。

## 验收门④：至少一个外部 MCP 服务被 Agent 真实调通 ✅

外部服务：官方 `mcp-server-fetch`（W12 #1 选型，ADR-0016）。

| 证据 | 内容 |
|---|---|
| W12 首验（2026-09-16） | traceId=`40c11ab7`，tool-audit `tool=fetchWebPage outcome=success elapsedMs=1930` |
| 今日复验（2026-09-18） | traceId=`c5f8a9ef`，tool-audit `tool=fetchWebPage args=[https://example.com] outcome=success elapsedMs=1474`；轨迹 2 步（fetchWebPage → getFinancialSummary），终答含网页摘要 + 600519 营收并标注来源/时点 |
| K8s 形态 | fetch 独立工作负载 + Streamable HTTP（W12 追加五，minikube 冒烟通过） |

**挂账观察更新**（"fetch 首两轮超时未定论"今日取证）：运行中实例连续 3 次协议超时
（30s，ReactiveException）；独立 stdio 探测同一服务 1.6s 正常返回；清洁重启应用后恢复
（1474ms 成功）。结论方向：**不是冷启动 TLS 缓存，是长驻实例的 MCP stdio 会话失活**——
子进程在但协议无响应，疑为 stdio 连接静默死亡且客户端无重连。转正式缺陷挂 W14
（MCP Client 健康检查/重连策略），m3 索引观察清单同步更新。

## 验收门⑤：混合检索 / rerank 由黄金集数据裁决 ✅

| 项 | 数据 | 裁决 |
|---|---|---|
| dense 基线 | Recall@5 21/29（72%） | — |
| hybrid（dense+sparse+RRF） | **27/29（93%，+21pp ≥ 80% 启用线）**；+6 条翻转零误伤，负例拒答 7/10 零回归 | ✅ 默认启用（`5f31315`） |
| hybrid + rerank | 27/29（93%，+0pp < 5pp 线）；39 条逐条对比零翻转，失败集合完全相同 | ⛔ 不启用，why-not + 重评触发在案 |

报告：[w13-hybrid-eval](../../eval/retrieval/w13-hybrid-eval.md) ·
[w13-rerank-eval](../../eval/retrieval/w13-rerank-eval.md)（39 条开/关逐条对比表） ·
chunk-size 三轮对照 [w13-chunk-size-comparison](../../eval/retrieval/w13-chunk-size-comparison.md)（维持 800）。

## M3 期间的关键演化（超出验收门的部分）

- 显式 ReAct 循环 + 协议解析容差（W11，ADR-0015）："静默降级是故障放大器"教训入档
- 会话记忆 L1 + SSE 轨迹推送（W10/W11）：对话产品形态成型
- 出入双向凭证（W12）：MCP Client + API Key 鉴权 + 限流 + 模型自动降级（FailoverChatModel）
- 检索两阶段化（W13，ADR-0017）：hybrid 启用 / rerank 落地但不启用——**数据裁决机制
  第三次给出答案（含两次否定）**，机制本身被验证可信

## 签署

W13 #4 复核：五门四过一警示（门③欠账两条已列清单，不阻塞收官——核心主张"改写收益保持、
总体不降级"成立）。**M3 收官**。下一阶段：W14+ 进阶清单见能力地图与 m3 索引挂账台账。

——2026-09-18
