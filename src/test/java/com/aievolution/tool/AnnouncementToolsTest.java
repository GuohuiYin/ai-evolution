package com.aievolution.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aievolution.rag.KnowledgeRetriever;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class AnnouncementToolsTest {

  private final KnowledgeRetriever knowledgeRetriever = mock(KnowledgeRetriever.class);
  private final AnnouncementTools tools = new AnnouncementTools(knowledgeRetriever);

  @Test
  void searchResultsCarrySourceFileNames() {
    when(knowledgeRetriever.retrieve("茅台的酿造工艺"))
        .thenReturn(List.of(new Document("茅台酿造工艺遵循12987流程", Map.of("source", "maotai.md"))));

    String output = tools.searchAnnouncements("茅台的酿造工艺");

    // 红线 03：检索片段必须带来源文件名，模型才有引用原料
    assertThat(output).contains("maotai.md").contains("12987");
  }

  @Test
  void emptySearchReturnsGracefulText() {
    when(knowledgeRetriever.retrieve("特斯拉自动驾驶")).thenReturn(List.of());

    assertThat(tools.searchAnnouncements("特斯拉自动驾驶")).contains("未找到");
  }
}
