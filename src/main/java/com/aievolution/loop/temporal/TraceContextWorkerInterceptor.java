package com.aievolution.loop.temporal;

import com.aievolution.infra.TraceContextPropagation;
import io.opentelemetry.context.Scope;
import io.temporal.common.interceptors.ActivityInboundCallsInterceptor;
import io.temporal.common.interceptors.ActivityInboundCallsInterceptorBase;
import io.temporal.common.interceptors.WorkerInterceptorBase;

/**
 * activity 执行期的链路上下文还原器（W25 #1a 收尾）：读取 {@link TraceContextPropagator} 交接来的 traceparent 装入 OTel
 * 当前上下文，activity 内的 span 全部挂回请求 trace； 执行结束作用域关闭，worker 线程复用不串号。
 */
public class TraceContextWorkerInterceptor extends WorkerInterceptorBase {

  @Override
  public ActivityInboundCallsInterceptor interceptActivity(ActivityInboundCallsInterceptor next) {
    return new ActivityInboundCallsInterceptorBase(next) {
      @Override
      public ActivityOutput execute(ActivityInput input) {
        try (Scope ignored =
            TraceContextPropagation.withRemoteParent(TraceContextPropagator.currentTraceparent())) {
          return super.execute(input);
        } finally {
          // worker 线程池复用：用完即清，下一个无 header 的 activity 不吃残留串号
          TraceContextPropagator.clearCurrent();
        }
      }
    };
  }
}
