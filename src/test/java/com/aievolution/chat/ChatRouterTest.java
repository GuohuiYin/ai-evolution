package com.aievolution.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.aievolution.chat.ChatRouter.Route;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 对话路由规则测试：路由决策是对话统一入口的核心策略，规则变化必须先改测试（TDD）。 */
class ChatRouterTest {

  private final ChatRouter router =
      new ChatRouter(List.of("股价", "行情", "走势", "营收", "净利", "利润", "财务"));

  @Test
  void stockCodeTriggersAgentRoute() {
    assertThat(router.route("600519 最近怎么样")).isEqualTo(Route.AGENT);
  }

  @Test
  void financeKeywordTriggersAgentRoute() {
    assertThat(router.route("茅台去年营收多少")).isEqualTo(Route.AGENT);
  }

  @Test
  void pureKnowledgeQuestionStaysOnRagRoute() {
    // 注意边界：含"年报"但无取数意图——仍走 RAG（Agent 也能答，但 RAG 有硬拒答闸门与结构化 sources）
    assertThat(router.route("茅台的酿造工艺有什么特点")).isEqualTo(Route.RAG);
    assertThat(router.route("年报里的工艺描述")).isEqualTo(Route.RAG);
  }

  @Test
  void blankMessageFallsBackToRag() {
    assertThat(router.route("")).isEqualTo(Route.RAG);
    assertThat(router.route(null)).isEqualTo(Route.RAG);
  }
}
