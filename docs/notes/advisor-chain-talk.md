# Advisor 链源码精读 · 30 分钟分享稿（W20 #3，文稿形式）

> 配套结构图：[../arch/advisor-chain.md](../arch/advisor-chain.md)。
> 分享对象：用过 ChatClient 但没读过其实现的工程师。目标：听完能不翻文档答出
> "加一层自定义越界拦截，钩子在哪"。

## 开场（2 分钟）：一个被忽视的真相

我们平时写 `chatClient.prompt().user("...").call().content()`，看起来是一行同步调用。
但 2.0.1 的源码里，`call()` 做的第一件事不是发请求，而是**现场装配一条责任链**——
模型调用只是链的最后一节。中间夹着的每一节都可以改你的 prompt、改你的响应，
甚至**不让你的请求到达模型**。今天 30 分钟，把这条链拆开。

## 第一段（6 分钟）：链是怎么建起来的

看 `DefaultChatClient.java:1177`：

```java
public CallResponseSpec call() {
    BaseAdvisorChain advisorChain = buildAdvisorChain();   // 每次调用现场建链
    return new DefaultCallResponseSpec(toChatClientRequest(this), advisorChain, ...);
}
```

`buildAdvisorChain()`（:1189-1203）三件事：

1. **自动注册 ToolCallingAdvisor**——除非显式关闭或链里已有别的 ToolAdvisor。
   并且全链最多一个 ToolAdvisor，两个直接抛异常（:1240）。这条校验告诉我们：
   工具循环在 2.0 里被收编成了链的一环，不是游离在外的逻辑。
2. **无条件追加两个链尾**：`ChatModelCallAdvisor` + `ChatModelStreamAdvisor`，
   order 都是 `LOWEST_PRECEDENCE`——它们是"终点"，不是可选项。
3. 全部塞进 `DefaultAroundAdvisorChain.Builder.pushAll()`，然后 `reOrder()`：
   `OrderComparator.sort`——**order 值小的排前面，最先执行，是最外层**。

心智模型一句话：**洋葱**。order 小的在最外皮，请求一层层剥进去，响应一层层裹出来。

## 第二段（7 分钟）：链是怎么跑起来的

`DefaultAroundAdvisorChain.nextCall()`（:98-121）只有七行有效代码：

```java
var advisor = this.callAdvisors.pop();
return observation.observe(() -> advisor.adviseCall(chatClientRequest, this));
```

注意第二个参数 `this`——**链把自己交给了 Advisor**。所以每个 Advisor 的标准姿势是：

```java
ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
    // before：改请求、查缓存、做校验……
    ChatClientResponse response = chain.nextCall(request);   // 放行进下一层
    // after：改响应、记账、打日志……
    return response;
}
```

两个推论：

- **短路**：不调 `chain.nextCall()` 直接 return，后面的层和模型调用全部不发生。
  这是拦截型 Advisor 的全部秘密。
- **每层自动包一个 Micrometer Observation**（span 名 = Advisor 名）——链上每一环的
  耗时、异常都自带留痕，不用自己埋点。

还有一个进阶武器 `copy(after)`（:169）：取某个 Advisor 之后的剩余子链。
`ToolCallingAdvisor` 靠它实现工具循环——每轮工具调用都 `chain.copy(this).nextCall(...)`
重进链尾（:167），于是排在它后面的 Advisor 能拦截**每一轮**工具迭代，而不是只看到
循环的入口和出口。

## 第三段（5 分钟）：写 Advisor 的两种姿势与 order 刻度

框架给了两层 API：

- **裸接口** `CallAdvisor` / `StreamAdvisor`：全手工，能短路；
- **模板接口** `BaseAdvisor`：只需覆写 `before(request)` 和 `after(response)`，
  框架替你调 nextCall。

**坑来了**：`BaseAdvisor.before` 返回的是 `ChatClientRequest`，模板拿到后无条件放行——
**before 没有"不往下走"的出口**。所以拦截型场景（敏感词、越界、配额超限）必须直接
实现 `CallAdvisor`。这不是我猜的：`SafeGuardAdvisor` 官方实现就没用 BaseAdvisor，
正是为了短路。

order 刻度表（背这几个就够）：

| 值 | 谁 | 含义 |
|---|---|---|
| 0 | SafeGuard / Logger / QA 默认 | 普通增强，链前段 |
| +200 | 记忆 Advisor 专用刻度 | 故意裹在工具循环外面 |
| +300 | ToolCallingAdvisor | 工具循环本体 |
| LOWEST-1 | 本项目 TokenUsageAdvisor | 贴链尾，拿最终真实请求 |
| LOWEST | ChatModelCallAdvisor | 终点，调模型 |

## 第四段（5 分钟）：上下文载体与三个官方范本

`ChatClientRequest(Prompt, Map context)` / `ChatClientResponse(ChatResponse, Map context)`——
两个 record，那个 `context` Map 是**全链数据总线**：

- `QuestionAnswerAdvisor`（RAG）：before 里向量检索 → 结果塞 `context["qa_retrieved_documents"]`
  → 模板改写 user 消息；after 里把文档从 context 搬进响应 metadata。**引用来源就是这么
  透出给调用方的**。
- 记忆 Advisor：从 context 取 `conversationId` 决定读写哪份历史。
- 结构化输出：格式指令经 context 传到链尾，由 `ChatModelCallAdvisor` 织入 prompt。

加上 `SafeGuardAdvisor`（短路拦截）、`SimpleLoggerAdvisor`（before/after 日志）、
`MessageChatMemoryAdvisor`（历史前插 + SystemMessage 置顶）——五个内置 Advisor，
恰好覆盖"拦截 / 观测 / 记忆 / 检索增强 / 工具循环"五种增强类型，是写自定义
Advisor 前最该抄的五份作业。

## 第五段（4 分钟）：照回本项目

我们自己的三个决定，现在都能对着源码说清为什么：

1. **TokenUsageAdvisor 挂 `LOWEST_PRECEDENCE - 1`**：贴链尾，拿到的是被所有上游
   Advisor 加工完、真正发往模型的请求，账才准。经 `ChatClientCustomizer` 全局织入，
   新增通路自动获得账本——开闭原则。
2. **Agent 通路弃用记忆 Advisor 改手工**：原 Advisor 的 before 在 ReAct 循环每一步
   都会重复追加同一条 user 消息——看完源码知道它是为"一次调用"设计的，套不进
   我们的显式循环。
3. **RAG 通路不用 QuestionAnswerAdvisor**：检索为空要直接拒答（防幻觉闸门）、引用
   来源要随响应返回（金融红线三）——黑盒 Advisor 的固定模板满足不了红线，手动编排。

## 收尾（1 分钟）：钩子自测

回到开场那道题：**加一层自定义越界拦截，钩子在哪？**

> 直接实现 `CallAdvisor`（别用 BaseAdvisor——它的 before 没法短路）；
> adviseCall 里检查 prompt，命中就构造响应直接返回、不调 `chain.nextCall()`；
> `getOrder()` 给小值（参照 SafeGuard 的 0）排到链前段；
> 注册走 `ChatClientCustomizer` 全局织入（本项目 `ModelObservabilityConfig` 有现成先例），
> 或调用点 `.advisors(...)` 单次挂载。

答得出这道题，今天 30 分钟就没白花。

## Q&A 预案

- **"和 Servlet Filter / Spring Interceptor 有什么区别？"** 同一套心智：Ordered 排序 +
  链式下钻 + 可短路。区别只在载体——Filter 拦的是 HttpServletRequest，Advisor 拦的
  是 ChatClientRequest（Prompt + context）。
- **"流式有什么不一样？"** 独立一条 streamAdvisors 链；`after` 只在 finishReason 帧
  执行；响应流被聚合器收集以回填观测。
- **"Advisor 和 AOP 什么关系？"** 是 AOP around advice 思想在调用链上的显式实现，
  但不是字节码/代理织入——就是普通的接口回调，可测性远好于真 AOP。
