package com.aievolution.loop.temporal;

import com.aievolution.loop.LoopListener;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * workflowId → {@link LoopListener} 的进程内桥（W21 #1②）：activity 由 worker 常驻线程执行， 而 listener（SSE
 * 推送）是每请求对象——activity 内经 {@code Activity.getExecutionContext()} 取 workflowId 回本表查回当前请求的
 * listener，保持逐步轨迹推送与 inline 引擎同行为。
 */
public class LoopListenerRegistry {

  private final Map<String, LoopListener> listeners = new ConcurrentHashMap<>();

  public void register(String workflowId, LoopListener listener) {
    this.listeners.put(workflowId, listener);
  }

  public void deregister(String workflowId) {
    this.listeners.remove(workflowId);
  }

  public LoopListener get(String workflowId) {
    return this.listeners.getOrDefault(workflowId, LoopListener.NONE);
  }
}
