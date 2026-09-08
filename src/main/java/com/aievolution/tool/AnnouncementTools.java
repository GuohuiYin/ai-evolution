package com.aievolution.tool;

import com.aievolution.rag.KnowledgeRetriever;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 公告/资料检索工具：把知识库语义检索包装为模型可自主调用的工具。
 *
 * <p>与 {@code RagChatService} 的差异：RAG 服务是"检索不到就拒答"的固定管道；本工具把
 * 检索能力交给模型按需调度——检索不到时返回提示文本，由模型结合上下文决定如何回应。
 */
@Component
public class AnnouncementTools {

  private final KnowledgeRetriever knowledgeRetriever;
  private final int maxQueryLength;

  public AnnouncementTools(
      KnowledgeRetriever knowledgeRetriever,
      @Value("${ai.tool.max-query-length:2000}") int maxQueryLength) {
    this.knowledgeRetriever = knowledgeRetriever;
    this.maxQueryLength = maxQueryLength;
  }

  @Tool(
      description =
          "检索知识库中的公司公告与研究资料（语义检索）。当问题涉及公司业务、工艺、战略、公告等" + "非数字信息时使用。返回资料片段并标注来源文件名，回答中必须引用来源。")
  public String searchAnnouncements(@ToolParam(description = "检索问题，用完整问句效果更好") String query) {
    // 红队基线 M3（P0）：超长参数直达上游付费 embedding API = 成本攻击面；上限外置 ai.tool.*（A11）
    if (query.length() > maxQueryLength) {
      throw new IllegalArgumentException(
          "query 超长（%d 字符，上限 %d）".formatted(query.length(), maxQueryLength));
    }
    List<Document> docs = knowledgeRetriever.retrieve(query);
    if (docs.isEmpty()) {
      return "知识库中未找到与问题相关的资料";
    }
    return docs.stream()
        .map(
            doc ->
                "【来源: %s】\n%s"
                    .formatted(
                        String.valueOf(doc.getMetadata().getOrDefault("source", "unknown")),
                        doc.getText()))
        .collect(Collectors.joining("\n---\n"));
  }
}
