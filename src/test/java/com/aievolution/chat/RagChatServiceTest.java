package com.aievolution.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aievolution.prompt.ClasspathPromptLibrary;
import com.aievolution.rag.KnowledgeRetriever;
import com.aievolution.rag.QueryRewriter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;

class RagChatServiceTest {

  private ChatClient chatClient;
  private ChatClient.ChatClientRequestSpec requestSpec;
  private KnowledgeRetriever knowledgeRetriever;
  private final ChatMemory chatMemory =
      MessageWindowChatMemory.builder()
          .chatMemoryRepository(new InMemoryChatMemoryRepository())
          .maxMessages(10)
          .build();
  private RagChatService service;

  @BeforeEach
  void setUp() {
    ChatClient.Builder builder = mock(ChatClient.Builder.class);
    chatClient = mock(ChatClient.class);
    requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
    ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
    knowledgeRetriever = mock(KnowledgeRetriever.class);

    when(builder.defaultAdvisors(any(Advisor.class))).thenReturn(builder);
    when(builder.build()).thenReturn(chatClient);
    when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
    when(requestSpec.advisors(any(java.util.function.Consumer.class))).thenReturn(requestSpec);
    when(requestSpec.call()).thenReturn(callSpec);
    when(callSpec.content()).thenReturn("模型回复");

    service =
        new RagChatService(
            builder,
            knowledgeRetriever,
            new ClasspathPromptLibrary(),
            "rag-chat-v1",
            chatMemory,
            (query, history) -> query, // 默认直通：检索行为测试聚焦检索本身
            false);
  }

  @Test
  void retrievalUsesRewrittenQuery() {
    // W11 #5：检索拿改写后的自足查询去向量化，而非原始追问（证据 #4 修复点）
    QueryRewriter rewriter = mock(QueryRewriter.class);
    when(rewriter.rewrite(org.mockito.ArgumentMatchers.eq("其中的 12987 是什么含义"), any()))
        .thenReturn("茅台 12987 工艺的含义");
    RagChatService rewritingService =
        new RagChatService(
            mock(ChatClient.Builder.class, org.mockito.Mockito.RETURNS_DEEP_STUBS),
            knowledgeRetriever,
            new ClasspathPromptLibrary(),
            "rag-chat-v1",
            chatMemory,
            rewriter,
            false);
    when(knowledgeRetriever.retrieve("茅台 12987 工艺的含义")).thenReturn(List.of());

    rewritingService.chat("其中的 12987 是什么含义", "conv-rw");

    verify(knowledgeRetriever).retrieve("茅台 12987 工艺的含义");
    verify(knowledgeRetriever, never()).retrieve("其中的 12987 是什么含义");
  }

  @Test
  void answersWithSourcesAndDisclaimerWhenKnowledgeFound() {
    when(knowledgeRetriever.retrieve("茅台营收多少？"))
        .thenReturn(
            List.of(
                new Document("id1", "营业总收入约1740亿元（数据时点：2024年年报）", Map.of("source", "maotai.md"))));

    ChatAnswer answer = service.chat("茅台营收多少？", "conv-t");

    assertThat(answer.reply()).startsWith("模型回复").endsWith("不构成投资建议。");
    assertThat(answer.sources())
        .singleElement()
        .satisfies(s -> assertThat(s.source()).isEqualTo("maotai.md"));
  }

  @Test
  void promptCarriesRetrievedContextAndUserQuestion() {
    when(knowledgeRetriever.retrieve("酿造工艺是什么？"))
        .thenReturn(List.of(new Document("id1", "酱香型白酒 12987 工艺", Map.of("source", "maotai.md"))));

    service.chat("酿造工艺是什么？", "conv-t");

    ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
    verify(chatClient).prompt(captor.capture());
    assertThat(captor.getValue().getContents()).contains("12987 工艺").contains("酿造工艺是什么？");
  }

  @Test
  void refusesWithoutCallingModelWhenNoKnowledgeFound() {
    when(knowledgeRetriever.retrieve("特斯拉怎么样？")).thenReturn(List.of());

    ChatAnswer answer = service.chat("特斯拉怎么样？", "conv-t");

    verify(chatClient, never()).prompt(any(Prompt.class)); // 无资料不幻觉：根本不调用模型
    assertThat(answer.sources()).isEmpty();
    assertThat(answer.reply()).contains("未找到").endsWith("不构成投资建议。");
  }

  // ── W24 #1b 零召回防线：换措辞重试（dense 阈值悬崖病灶） ──

  private RagChatService retryService(QueryRewriter rewriter) {
    return new RagChatService(
        mock(ChatClient.Builder.class, org.mockito.Mockito.RETURNS_DEEP_STUBS),
        knowledgeRetriever,
        new ClasspathPromptLibrary(),
        "rag-chat-v1",
        chatMemory,
        rewriter,
        true);
  }

  @Test
  void zeroRecallRetryRecoversWithRephrasedVariant() {
    // 首召空 → 重述变体 → 二召命中：答案照常出（涉案案「12987 是什么含义」的修复路径）
    QueryRewriter rewriter = mock(QueryRewriter.class);
    when(rewriter.rewrite(org.mockito.ArgumentMatchers.eq("其中的 12987 是什么含义"), any()))
        .thenReturn("贵州茅台酿造工艺中的12987是什么含义");
    when(rewriter.rephrase("贵州茅台酿造工艺中的12987是什么含义")).thenReturn("茅台 12987 工艺 含义");
    when(knowledgeRetriever.retrieve("贵州茅台酿造工艺中的12987是什么含义")).thenReturn(List.of());
    when(knowledgeRetriever.retrieve("茅台 12987 工艺 含义"))
        .thenReturn(
            List.of(new Document("id1", "12987：两次投料、九次蒸煮…", Map.of("source", "maotai.md"))));

    ChatAnswer answer = retryService(rewriter).chat("其中的 12987 是什么含义", "conv-retry");

    // 关键断言：拒答被解除、来源来自二召变体（deep-stub 客户端 reply 为空串+免责声明，
    // 检索行为才是本测试焦点）
    assertThat(answer.reply()).doesNotContain("未找到").endsWith("不构成投资建议。");
    assertThat(answer.sources())
        .singleElement()
        .satisfies(s -> assertThat(s.source()).isEqualTo("maotai.md"));
    verify(knowledgeRetriever).retrieve("贵州茅台酿造工艺中的12987是什么含义");
    verify(knowledgeRetriever).retrieve("茅台 12987 工艺 含义");
  }

  @Test
  void zeroRecallRetryStillRefusesWhenVariantAlsoEmpty() {
    // 重述后仍空 → 硬拒答红线语义不变（越界问题的防线不被重试削弱）
    QueryRewriter rewriter = mock(QueryRewriter.class);
    when(rewriter.rewrite(any(), any())).thenReturn("特斯拉 2024 营收");
    when(rewriter.rephrase("特斯拉 2024 营收")).thenReturn("特斯拉 年度营收 规模");
    when(knowledgeRetriever.retrieve(any())).thenReturn(List.of());

    ChatAnswer answer = retryService(rewriter).chat("特斯拉营收多少", "conv-retry2");

    assertThat(answer.reply()).contains("未找到");
    verify(knowledgeRetriever).retrieve("特斯拉 2024 营收");
    verify(knowledgeRetriever).retrieve("特斯拉 年度营收 规模");
  }

  @Test
  void retrySkippedWhenRephraseReturnsSameQuery() {
    // 重述给不出变体（返回原查询）→ 跳过二次检索：同查询重试纯属浪费
    QueryRewriter rewriter = mock(QueryRewriter.class);
    when(rewriter.rewrite(any(), any())).thenReturn("原查询");
    when(rewriter.rephrase("原查询")).thenReturn("原查询");
    when(knowledgeRetriever.retrieve("原查询")).thenReturn(List.of());

    retryService(rewriter).chat("任意问题", "conv-retry3");

    verify(knowledgeRetriever, org.mockito.Mockito.times(1)).retrieve("原查询");
  }

  @Test
  void retryDisabledKeepsOldSemantics() {
    // 开关关闭（默认）：空召回即拒答，不重述不再试——行为与 W23 前完全一致
    QueryRewriter rewriter = mock(QueryRewriter.class);
    when(rewriter.rewrite(any(), any())).thenReturn("空召回查询");
    when(knowledgeRetriever.retrieve("空召回查询")).thenReturn(List.of());
    RagChatService offService =
        new RagChatService(
            mock(ChatClient.Builder.class, org.mockito.Mockito.RETURNS_DEEP_STUBS),
            knowledgeRetriever,
            new ClasspathPromptLibrary(),
            "rag-chat-v1",
            chatMemory,
            rewriter,
            false);

    offService.chat("任意问题", "conv-retry4");

    verify(knowledgeRetriever, org.mockito.Mockito.times(1)).retrieve("空召回查询");
    verify(rewriter, never()).rephrase(any());
  }
}
