# Temporal 最小环实录 + 语义笔记（W21 #1①）

> 2026-10-22。目的：蓝图"Temporal 到会调"的第一环——本地跑通 + 弄清
> workflow/activity/retry/query/确定性五个核心语义，为 #1②（loop run 状态持久化
> 裁决）提供一手依据。
>
> ⚠️ 脚手架已拆除（W21 #1③ 同批）：`temporal/hello/` 5 类 + `HelloLoopWorkflowTest`
> 已删——其验证的语义已由 #1② 真实 `loop/temporal/ResearchLoopWorkflow` 继承覆盖，
> 本笔记作为历史实录保留，代码引用失效以 git 历史（d944187）为准。

## 环境

- Temporal CLI 1.9.1（brew），`temporal server start-dev`：gRPC `localhost:7233`、
  UI `http://localhost:8233`、persistence in-memory（dev 模式）
- SDK：`io.temporal:temporal-sdk:1.29.0` + `temporal-testing:1.29.0`（test scope）
- 环境坑位二则：① CLI 直连 `localhost` 会优先解析 IPv6 `::1` 而 server 绑在 IPv4——
  CLI 需显式 `--address 127.0.0.1:7233`（Java SDK 无此问题）；② dev server 用 nohup 常驻，
  停法 `pkill -f "temporal server start-dev"`

## 最小环设计（刻意贴合目标场景）

不用教科书的"单次调用 hello"，直接做**带状态的三步循环**——因为目标场景是
ResearchLoop run 状态持久化，最小环必须验证四件事：

1. workflow 内状态（`progress` 字段）运行中可随时 Query；
2. 每步副作用外包给 activity（workflow 本体保持确定性）；
3. activity 抛错由**服务端**按 RetryOptions 重试，workflow 代码零感知；
4. 全程事件历史持久化、可回放查看。

## 实录（真机，dev server）

```
--- workflow result ---
step 1 processed [ai-evolution W21 最小环] attempts(step2)=0
step 2 processed [ai-evolution W21 最小环] attempts(step2)=2   ← 首次故意抛错，服务端重试成功
step 3 processed [ai-evolution W21 最小环] attempts(step2)=2
--- progress query after completion ---
completed
```

事件历史（`temporal workflow show`）：23 个事件全留痕——WorkflowExecutionStarted →
每步 WorkflowTask(Scheduled/Started/Completed) + ActivityTask(Scheduled/Started/Completed)
→ WorkflowExecutionCompleted，结果 JSON 内嵌可查。注意：server 1.32 下**可重试的
activity 失败不再写 ActivityTaskFailed 历史事件**（减历史体积），尝试计数在
pending activity 信息里——排障时别在历史里找失败事件，直接看 attempts。

单测（已随脚手架拆除，见文首说明）：`HelloLoopWorkflowTest` 绿——TestWorkflowEnvironment 进程内测试服务器
（时间跳跃，重试 backoff 不真实等待），锁定三条语义：结果拼接正确 /
`attempts(step2)=2` 重试生效 / Query 读到终态 `completed`。

## 五语义笔记（对照我们的 ReAct 循环）

| 语义 | Temporal 机制 | 对照 ResearchLoop 现状 |
|---|---|---|
| **Workflow** | 确定性编排函数；历史事件重放恢复内存状态；崩溃后从最后 checkpoint 重放续跑 | ResearchLoop 的 `run()` 内存循环——进程死即丢，无恢复 |
| **Activity** | 一切副作用（IO/外部调用/非确定性）的唯一出口；独立于 workflow 重试 | 我们的工具调用/模型调用直接内联在循环里 |
| **Retry** | RetryOptions 声明式（最大尝试/退避），服务端调度，workflow 代码零感知 | 手工 max-steps=6 预算控制，无单步重试语义 |
| **Query** | 同步读 workflow 当前状态，不进历史、不重放 | LoopListener 观测端口是我们自造的对应物 |
| **确定性约束** | workflow 内禁时间/随机/线程/直连 IO（重放分叉），用 `Workflow.*` API 替代 | 无此约束（也无重放能力）——这是引入 Temporal 的最大心智成本 |

## 初步判断（带裁决 #1②）

- **Temporal 给的正好是我们的缺口**：run 状态持久化（事件历史即 checkpoint）、
  断点恢复（重放续跑）、单步重试（声明式）——三件事我们目前全是零。
- **代价也已看清**：workflow 确定性约束意味着 ReAct 循环体要改造（模型调用/工具调用
  全部下沉为 activity）；对 6 步以内的最小研究循环，这是"用工业重机开瓶盖"的
  风险——替换范围裁决（全替换 vs 只接管 run 状态持久化）留给 #1②。
