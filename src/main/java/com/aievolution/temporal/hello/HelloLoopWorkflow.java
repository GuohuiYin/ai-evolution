package com.aievolution.temporal.hello;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * W21 #1 最小环 workflow：三步循环 + 进度可查询。
 *
 * <p>刻意选"带状态的步进循环"而非教科书的单次调用——目标场景是 ResearchLoop run 状态持久化， 最小环就要验证：① workflow 内状态（{@link
 * #progress}）可随时 Query；② 每步调 activity （副作用外包，workflow 本体保持确定性）；③ activity 失败由服务端按 RetryOptions 重试，
 * workflow 代码零感知。
 */
@WorkflowInterface
public interface HelloLoopWorkflow {

  @WorkflowMethod
  String run(String input);

  /** 进度查询：Temporal 的 Query 语义——不重放历史，直接读 workflow 当前内存状态。 */
  @QueryMethod
  String progress();
}
