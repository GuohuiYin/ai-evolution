package com.aievolution.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

/** 检索日志测试：检索是 RAG 的"裁判"，判罚依据必须可见—— hits/topScore/threshold 三键是排查"为什么拒答/为什么答错"的唯一现场。 */
class VectorStoreKnowledgeRetrieverLoggingTest {

  private final VectorStore vectorStore = mock(VectorStore.class);
  private final VectorStoreKnowledgeRetriever retriever =
      new VectorStoreKnowledgeRetriever(vectorStore, 0.5, 5);
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void attachAppender() {
    ch.qos.logback.classic.Logger logger =
        (ch.qos.logback.classic.Logger)
            LoggerFactory.getLogger(VectorStoreKnowledgeRetriever.class);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  private String lastLog() {
    assertThat(appender.list).as("检索必须留日志").isNotEmpty();
    return appender.list.getLast().getFormattedMessage();
  }

  @Test
  void hitCaseLogsHitsAndTopScore() {
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(
            List.of(
                Document.builder().text("命中一").score(0.83).build(),
                Document.builder().text("命中二").score(0.61).build()));

    retriever.retrieve("酿造工艺");

    assertThat(lastLog())
        .contains("stage=RETRIEVE")
        .contains("hits=2")
        .contains("topScore=0.83")
        .contains("threshold=0.5");
  }

  @Test
  void emptyResultAlsoLogged() {
    // 拒答场景（如 12987 问法）：hits=0 的日志是"为什么没答"的直接证据
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

    retriever.retrieve("12987 工艺");

    assertThat(lastLog()).contains("hits=0");
  }
}
