package com.aievolution.loop.pipeline;

import com.aievolution.loop.ToolExecutor;
import com.aievolution.rag.QueryRewriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;

/**
 * 追问改写的工具执行装饰（W27 #2b）：pipeline 检索通路的 {@code searchAnnouncements} 查询先经 {@link QueryRewriter}
 * 用会话历史做指代消解，再下放检索——消除不对称：chat 通路（RagChatService W11 #5 起）检索前必过改写，pipeline 通路（W22 起）却拿模型临场写的 query
 * 裸奔向量化（W26 多轮拒答根因的检索侧一半）。
 *
 * <p>拦截面与通路白名单同源（{@link ChannelToolExecutor#toolsFor}，A11 不出现第二处工具名副本）；
 * 改写器关闭档（PassThrough）时本装饰零行为变化。
 *
 * <p>失败哲学同 {@code ToolRegistry}：入参 JSON 畸形/缺 query 字段不拦截，原样透传交下游产错误观察喂回模型；
 * 改写器自身已含降级（模型不可用/空白输出返回原查询）。
 */
public class QueryRewritingToolExecutor implements ToolExecutor {

  private static final Logger log = LoggerFactory.getLogger(QueryRewritingToolExecutor.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** 需要改写的工具 = RAG 通路白名单（检索类工具的唯一事实源，不通告第二份名单） */
  private static final Set<String> REWRITABLE_TOOLS = ChannelToolExecutor.toolsFor(Channel.RAG);

  private final ToolExecutor delegate;
  private final QueryRewriter queryRewriter;
  private final List<Message> history;

  public QueryRewritingToolExecutor(
      ToolExecutor delegate, QueryRewriter queryRewriter, List<Message> history) {
    this.delegate = delegate;
    this.queryRewriter = queryRewriter;
    this.history = history;
  }

  @Override
  public String execute(String tool, String input) {
    if (!REWRITABLE_TOOLS.contains(tool)) {
      return this.delegate.execute(tool, input);
    }
    return this.delegate.execute(tool, rewriteQuery(input));
  }

  private String rewriteQuery(String input) {
    JsonNode args;
    try {
      args = MAPPER.readTree(input);
    } catch (Exception e) {
      return input; // JSON 畸形原样透传：下游「入参解析失败」错误观察语义不变
    }
    JsonNode query = args.get("query");
    if (!args.isObject() || query == null || !query.isTextual()) {
      return input;
    }
    String origin = query.asText();
    String rewritten = this.queryRewriter.rewrite(origin, this.history);
    if (rewritten.equals(origin)) {
      return input;
    }
    log.info("stage=PIPELINE_REWRITE origin={} rewritten={}", origin, rewritten);
    return ((ObjectNode) args).put("query", rewritten).toString();
  }
}
