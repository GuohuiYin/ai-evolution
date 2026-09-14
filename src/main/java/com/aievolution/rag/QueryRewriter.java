package com.aievolution.rag;

import java.util.List;
import org.springframework.ai.chat.messages.Message;

/**
 * 查询改写器契约（W11 #5，A10 IOP）：基于会话历史把追问改写为自足查询（指代消解）。
 *
 * <p>动机（证据 #4，W10 实锤）：记忆 advisor 只解决了模型侧上下文，检索拿原始追问去向量化， "其中的 12987" hits=0 硬拒答——改写是多轮 RAG 的咽喉。
 */
public interface QueryRewriter {

  /**
   * 把追问改写为自足查询。
   *
   * @param query 用户本轮原始输入
   * @param history 会话历史（按时间序；空 = 首轮）
   * @return 自足查询；无法改写时返回原 query（兜底，永不返回空）
   */
  String rewrite(String query, List<Message> history);
}
