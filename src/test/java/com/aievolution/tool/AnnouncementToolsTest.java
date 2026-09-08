package com.aievolution.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aievolution.rag.KnowledgeRetriever;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class AnnouncementToolsTest {

  private final KnowledgeRetriever knowledgeRetriever = mock(KnowledgeRetriever.class);
  private final AnnouncementTools tools = new AnnouncementTools(knowledgeRetriever, 2000);

  @Test
  void oversizedQueryRejectedBeforeRetrieval() {
    // 红队基线 M3（P0）：超长参数直达上游付费 embedding API = 成本攻击面。
    // 防线必须在工具入口、上游调用之前
    String hugeQuery = "A".repeat(2001);

    assertThatThrownBy(() -> tools.searchAnnouncements(hugeQuery))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("超长");

    verifyNoInteractions(knowledgeRetriever);
  }

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
