package com.aievolution.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * 知识库摄入器（RAG 的 ETL 管道）：启动时读取知识库目录，按扩展名分派解析器（.md 纯文本 / .pdf 分页读取），分块后经 EmbeddingModel 向量化写入
 * VectorStore。
 *
 * <p>元数据规范（红线 03 的结构化落地）：每个源文件可伴随 {@code <文件名>.meta.json} 声明 {@code
 * docType}（report/announcement/note）与 {@code asOf}（数据时点）；缺省 docType=note。 元数据进 Qdrant
 * payload，支撑检索期过滤（{@link KnowledgeFilter}）。
 *
 * <p>幂等设计：文档 ID 由 {@code 文件名#块序号} 确定性生成（UUIDv3 语义）， 配合 Qdrant 的 upsert 语义，重复启动不会产生重复向量。
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
  private static final String DEFAULT_LOCATION = "classpath:knowledge/**/*";

  private final VectorStore vectorStore;
  private final String knowledgeLocation;
  private final ResourcePatternResolver resourceResolver =
      new PathMatchingResourcePatternResolver();
  private final ObjectMapper objectMapper = new ObjectMapper();
  // 分块大小是 RAG 最经典的调参项（与检索质量直接相关），显式配置化而非吃库默认值（约定 A11-2）
  private final TextSplitter textSplitter;

  public KnowledgeBaseIngestor(
      VectorStore vectorStore,
      @Value("${ai.rag.chunk-size:800}") int chunkSize,
      @Value("${ai.knowledge.location:" + DEFAULT_LOCATION + "}") String knowledgeLocation) {
    this.vectorStore = vectorStore;
    this.knowledgeLocation = knowledgeLocation;
    this.textSplitter = TokenTextSplitter.builder().withChunkSize(chunkSize).build();
  }

  @Override
  public void run(ApplicationArguments args) throws Exception {
    Resource[] resources =
        Arrays.stream(resourceResolver.getResources(knowledgeLocation))
            .filter(r -> r.getFilename() != null && isSupported(r.getFilename()))
            .toArray(Resource[]::new);
    List<Document> chunks = new ArrayList<>();
    for (Resource resource : resources) {
      Map<String, Object> metadata = resolveMetadata(resource);
      List<Document> pages = readDocuments(resource, metadata);
      List<Document> pieces = textSplitter.split(pages);
      for (int i = 0; i < pieces.size(); i++) {
        String id =
            UUID.nameUUIDFromBytes(
                    (metadata.get("source") + "#" + i).getBytes(StandardCharsets.UTF_8))
                .toString();
        chunks.add(new Document(id, pieces.get(i).getText(), pieces.get(i).getMetadata()));
      }
    }
    if (!chunks.isEmpty()) {
      vectorStore.add(chunks);
      log.info("知识库摄入完成：{} 个文档 → {} 个分块", resources.length, chunks.size());
    } else {
      log.warn("知识库目录为空（{}），RAG 检索将无内容可召回", knowledgeLocation);
    }
  }

  /** 支持的源文件类型白名单：.md 纯文本 / .pdf 分页读取（目录与 meta.json 边车自然排除）。 */
  private boolean isSupported(String filename) {
    String lower = filename.toLowerCase();
    return lower.endsWith(".md") || lower.endsWith(".pdf");
  }

  /** 按扩展名分派解析器：.pdf 分页读取，.md 按纯文本。 */
  private List<Document> readDocuments(Resource resource, Map<String, Object> metadata)
      throws Exception {
    String filename = resource.getFilename();
    if (filename != null && filename.toLowerCase().endsWith(".pdf")) {
      return new PagePdfDocumentReader(resource)
          .get().stream().map(d -> new Document(d.getText(), new HashMap<>(metadata))).toList();
    }
    return List.of(new Document(resource.getContentAsString(StandardCharsets.UTF_8), metadata));
  }

  /** 元数据三件套：source 必有；docType/asOf 来自同目录伴随 meta.json，缺省 docType=note。 */
  private Map<String, Object> resolveMetadata(Resource resource) throws Exception {
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("source", resource.getFilename());
    metadata.put("docType", "note");
    Resource sidecar = resource.createRelative(resource.getFilename() + ".meta.json");
    if (sidecar.exists()) {
      objectMapper
          .readValue(sidecar.getInputStream(), Map.class)
          .forEach((k, v) -> metadata.put(String.valueOf(k), v));
    }
    return metadata;
  }
}
