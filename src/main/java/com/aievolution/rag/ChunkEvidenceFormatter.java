package com.aievolution.rag;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.ai.document.Document;

/**
 * 检索片段进上下文的统一格式（W26 #5 攻坚收口，A11-1 两次即收口 + A12 溯源单点化）。
 *
 * <p>此前存在两处格式分叉：RAG 问答通路（RagChatService.joinContents）裸拼文本，工具通路
 * （AnnouncementTools）带【来源】头——前者导致模型看不到片段的出处与数据时点，「最新口径优先」 排序与 prompt
 * 条款都因此失效（总股本/员工总数两案根因：模型不知道哪个片段是最新披露）。
 *
 * <p>统一格式：每个片段带【来源: 文件名，数据时点: asOf】头（无 asOf 的笔记类片段只带来源）， 片段间以 {@code ---} 分隔——红线三「数字必须可溯源」的供给侧原料。
 */
public final class ChunkEvidenceFormatter {

  private ChunkEvidenceFormatter() {}

  /** 单片段 → 带来源与时点头的上下文块；source 缺失标 unknown，asOf 缺失不展示。 */
  public static String toContextBlock(Document doc) {
    Object source = doc.getMetadata().getOrDefault("source", "unknown");
    Object asOf = doc.getMetadata().get("asOf");
    String header =
        asOf == null ? "【来源: %s】".formatted(source) : "【来源: %s，数据时点: %s】".formatted(source, asOf);
    return header + "\n" + doc.getText();
  }

  /** 片段列表 → 上下文整体（--- 分隔，保持入参顺序——排序语义归检索层）。 */
  public static String joinForContext(List<Document> docs) {
    return docs.stream()
        .map(ChunkEvidenceFormatter::toContextBlock)
        .collect(Collectors.joining("\n---\n"));
  }
}
