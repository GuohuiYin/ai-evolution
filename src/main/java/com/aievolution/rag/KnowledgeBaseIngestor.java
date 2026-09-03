package com.aievolution.rag;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * 知识库摄入器（RAG 的 ETL 管道）：启动时读取 {@code classpath:knowledge/*.md}， 分块后经 EmbeddingModel 向量化写入
 * VectorStore。
 *
 * <p>幂等设计：文档 ID 由 {@code 文件名#块序号} 确定性生成（UUIDv3 语义）， 配合 Qdrant 的 upsert 语义，重复启动不会产生重复向量——新增文档只需把 .md
 * 放入 knowledge/ 目录重启即可。
 */
// 开关语义：默认开启摄入；测试环境通过 ai.knowledge.ingest.enabled=false 关闭，
// 避免 @SpringBootTest 执行 ApplicationRunner 时打真实 Embedding API
@Component
@ConditionalOnProperty(
    name = "ai.knowledge.ingest.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class KnowledgeBaseIngestor implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseIngestor.class);
  private static final String KNOWLEDGE_LOCATION = "classpath:knowledge/*.md";

  private final VectorStore vectorStore;
  private final ResourcePatternResolver resourceResolver =
      new PathMatchingResourcePatternResolver();
  private final TextSplitter textSplitter = new TokenTextSplitter();

  public KnowledgeBaseIngestor(VectorStore vectorStore) {
    this.vectorStore = vectorStore;
  }

  @Override
  public void run(ApplicationArguments args) throws Exception {
    Resource[] resources = resourceResolver.getResources(KNOWLEDGE_LOCATION);
    List<Document> chunks = new ArrayList<>();
    for (Resource resource : resources) {
      String source = resource.getFilename();
      String text = resource.getContentAsString(StandardCharsets.UTF_8);
      List<Document> pieces =
          textSplitter.split(List.of(new Document(text, Map.of("source", source))));
      for (int i = 0; i < pieces.size(); i++) {
        String id =
            UUID.nameUUIDFromBytes((source + "#" + i).getBytes(StandardCharsets.UTF_8)).toString();
        chunks.add(new Document(id, pieces.get(i).getText(), pieces.get(i).getMetadata()));
      }
    }
    if (!chunks.isEmpty()) {
      vectorStore.add(chunks);
      log.info("知识库摄入完成：{} 个文档 → {} 个分块", resources.length, chunks.size());
    } else {
      log.warn("知识库目录为空（{}），RAG 检索将无内容可召回", KNOWLEDGE_LOCATION);
    }
  }
}
