package com.aievolution.loop.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.aievolution.loop.ToolExecutor;
import com.aievolution.rag.PassThroughQueryRewriter;
import com.aievolution.rag.QueryRewriter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * W27 #2b：pipeline 检索通路的追问改写装饰——searchAnnouncements 的 query 先过指代消解再检索； 非检索工具与畸形入参原样透传（失败哲学同
 * ToolRegistry）。
 */
class QueryRewritingToolExecutorTest {

  /** 记录每次收到的工具与入参的桩执行器 */
  private static class RecordingExecutor implements ToolExecutor {
    final List<String> calls = new ArrayList<>();

    @Override
    public String execute(String tool, String input) {
      this.calls.add(tool + "|" + input);
      return "观察";
    }
  }

  /** 脚本化改写器：记录收到的问题与历史，返回预设改写结果 */
  private static class ScriptedRewriter implements QueryRewriter {
    String rewritten;
    String receivedQuery;
    List<Message> receivedHistory;

    @Override
    public String rewrite(String query, List<Message> history) {
      this.receivedQuery = query;
      this.receivedHistory = history;
      return this.rewritten;
    }
  }

  private static List<Message> followUpHistory() {
    return List.of(new UserMessage("茅台 2024 年营收多少"), new AssistantMessage("2024 年营收 1700 亿元"));
  }

  @Test
  void searchQueryRewrittenWithHistory() {
    RecordingExecutor delegate = new RecordingExecutor();
    ScriptedRewriter rewriter = new ScriptedRewriter();
    rewriter.rewritten = "贵州茅台（600519）2024 年营收";

    new QueryRewritingToolExecutor(delegate, rewriter, followUpHistory())
        .execute("searchAnnouncements", "{\"query\":\"它的营收\"}");

    // 改写器收到原始 query 与会话历史；下放给检索的是改写后的自足查询
    assertThat(rewriter.receivedQuery).isEqualTo("它的营收");
    assertThat(rewriter.receivedHistory).hasSize(2);
    assertThat(delegate.calls)
        .containsExactly("searchAnnouncements|{\"query\":\"贵州茅台（600519）2024 年营收\"}");
  }

  @Test
  void nonRetrievalToolsPassThroughUntouched() {
    RecordingExecutor delegate = new RecordingExecutor();
    ScriptedRewriter rewriter = new ScriptedRewriter();

    new QueryRewritingToolExecutor(delegate, rewriter, followUpHistory())
        .execute("getFinancialSummary", "{\"code\":\"600519\",\"fiscalYear\":2024}");

    assertThat(delegate.calls)
        .containsExactly("getFinancialSummary|{\"code\":\"600519\",\"fiscalYear\":2024}");
    assertThat(rewriter.receivedQuery).isNull();
  }

  @Test
  void malformedInputPassesThroughAsIs() {
    RecordingExecutor delegate = new RecordingExecutor();
    ScriptedRewriter rewriter = new ScriptedRewriter();

    new QueryRewritingToolExecutor(delegate, rewriter, followUpHistory())
        .execute("searchAnnouncements", "不是JSON");

    // 原样透传：下游「入参解析失败」错误观察语义不变
    assertThat(delegate.calls).containsExactly("searchAnnouncements|不是JSON");
    assertThat(rewriter.receivedQuery).isNull();
  }

  @Test
  void unchangedRewriteKeepsOriginalInput() {
    // 首轮/无需改写：改写器原样返回时不重写 JSON（零行为变化）
    RecordingExecutor delegate = new RecordingExecutor();
    String input = "{\"query\":\"贵州茅台营收\"}";

    new QueryRewritingToolExecutor(delegate, new PassThroughQueryRewriter(), List.of())
        .execute("searchAnnouncements", input);

    assertThat(delegate.calls).containsExactly("searchAnnouncements|" + input);
  }
}
