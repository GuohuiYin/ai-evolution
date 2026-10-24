package com.aievolution.loop.pipeline;

import java.util.List;
import org.springframework.ai.chat.messages.Message;

/** Planner 角色端口（W22 #1b，设计 §1.3）：纯规划，无工具、不碰数据—— 把用户问题（+会话历史）拆解为研究计划。 */
public interface Planner {

  /** 拆解为研究计划。实现方对"计划不可解析"负责降级为单子问题保底 （不抛异常阻塞链路）。 */
  Plan plan(String question, List<Message> history);
}
