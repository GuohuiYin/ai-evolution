package com.aievolution.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.springframework.core.annotation.Order;
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
 * <p>W8-2 增量摄入：按文件内容 SHA-256 对比 {@link KnowledgeManifest}——未变文件整块跳过（零 embedding
 * 调用），新增/变更文件整删重写（同时修复"分块数变少导致旧块残留"），磁盘上消失的文件清理对应向量。 文档 ID 仍由 {@code 文件名#块序号} 确定性生成（UUIDv3
 * 语义），同内容重写不产生新 ID。
 *
 * <p>W13-1 增补：判变键并入分块参数哈希（{@link KnowledgeManifest#chunkSignature()}）。chunk-size 变更不改变文件
 * SHA，单靠内容哈希会拿旧分块服务（W13 #1 对照实验实测坑）——签名不符即全量重建， 旧块按 manifest 记录的 chunkIds 逐文件清理，无需手工清库。
 */
// 开关语义：默认开启摄入；测试环境通过 ai.knowledge.ingest.enabled=false 关闭，
// 避免 @SpringBootTest 执行 ApplicationRunner 时打真实 Embedding API
// @Order(1)：摄入必须先于 eval Runner（W11 #7 CI 起全新 Qdrant，顺序未定会导致 eval 在空库上跑）
@Component
@Order(1)
@ConditionalOnProperty(
    name = "ai.knowledge.ingest.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class KnowledgeBaseIngestor implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseIngestor.class);
  private static final String DEFAULT_LOCATION = "classpath:knowledge/**/*";
  private static final String DEFAULT_MANIFEST_PATH = "build/knowledge-manifest.json";

  private final VectorStore vectorStore;
  private final String knowledgeLocation;
  private final Path manifestPath;
  private final ResourcePatternResolver resourceResolver =
      new PathMatchingResourcePatternResolver();
  private final ObjectMapper objectMapper = new ObjectMapper();
  // 分块大小是 RAG 最经典的调参项（与检索质量直接相关），显式配置化而非吃库默认值（约定 A11-2）
  private final TextSplitter textSplitter;
  private final int chunkSize;

  public KnowledgeBaseIngestor(
      VectorStore vectorStore,
      @Value("${ai.rag.chunk-size:800}") int chunkSize,
      @Value("${ai.knowledge.location:" + DEFAULT_LOCATION + "}") String knowledgeLocation,
      @Value("${ai.knowledge.manifest-path:" + DEFAULT_MANIFEST_PATH + "}") String manifestPath) {
    this.vectorStore = vectorStore;
    this.knowledgeLocation = knowledgeLocation;
    this.manifestPath = Path.of(manifestPath);
    this.chunkSize = chunkSize;
    this.textSplitter = TokenTextSplitter.builder().withChunkSize(chunkSize).build();
  }

  @Override
  public void run(ApplicationArguments args) throws Exception {
    Resource[] resources =
        Arrays.stream(resourceResolver.getResources(knowledgeLocation))
            .filter(r -> r.getFilename() != null && isSupported(r.getFilename()))
            .toArray(Resource[]::new);

    KnowledgeManifest manifest = new KnowledgeManifest(manifestPath);
    // 分块参数并入判变键：签名不符说明旧向量是按另一组参数切的，全部作废重建
    String signature = splitterSignature();
    boolean splitterChanged = !signature.equals(manifest.chunkSignature());
    if (splitterChanged && !manifest.entries().isEmpty()) {
      log.info("分块参数变更（当前 chunk-size={}），已有向量全部作废，执行全量重建", chunkSize);
    }
    Set<String> present = new HashSet<>();
    int skipped = 0;
    int reIngested = 0;

    for (Resource resource : resources) {
      String filename = resource.getFilename();
      present.add(filename);
      String sha256 = sha256(resource);
      KnowledgeManifest.Entry old = manifest.entries().get(filename);
      if (old != null && !splitterChanged && old.sha256().equals(sha256)) {
        skipped++;
        continue;
      }
      if (old != null) {
        // 变更文件：整删旧向量再重写，避免分块数变少时旧块残留
        vectorStore.delete(old.chunkIds());
      }
      List<Document> chunks = chunk(resource);
      if (chunks.isEmpty()) {
        // TokenTextSplitter 默认丢弃短于 minChunkSizeChars 的文本——短文件会零分块，必须告警
        log.warn("文件 {} 未产生任何分块（内容过短或为空？），不会进入向量库", filename);
      } else {
        vectorStore.add(chunks);
      }
      manifest.put(
          filename,
          new KnowledgeManifest.Entry(sha256, chunks.stream().map(Document::getId).toList()));
      reIngested++;
    }

    // 磁盘上消失的文件：清理对应向量，保持库与目录一致
    int removed = 0;
    for (String filename : new HashSet<>(manifest.entries().keySet())) {
      if (!present.contains(filename)) {
        vectorStore.delete(manifest.entries().get(filename).chunkIds());
        manifest.remove(filename);
        removed++;
      }
    }

    manifest.chunkSignature(signature);
    manifest.save();
    if (reIngested == 0 && removed == 0) {
      log.info("知识库摄入完成：{} 个文件全部未变更，跳过向量化（零 embedding 调用）", skipped);
    } else {
      log.info("知识库摄入完成：重写 {} 个文件，跳过 {} 个未变文件，清理 {} 个已删除文件", reIngested, skipped, removed);
    }
  }

  /** 支持的源文件类型白名单：.md 纯文本 / .pdf 分页读取（目录与 meta.json 边车自然排除）。 */
  private boolean isSupported(String filename) {
    String lower = filename.toLowerCase();
    return lower.endsWith(".md") || lower.endsWith(".pdf");
  }

  /** 单文件分块：解析 → 分块 → 确定性 ID。 */
  private List<Document> chunk(Resource resource) throws Exception {
    Map<String, Object> metadata = resolveMetadata(resource);
    List<Document> pages = readDocuments(resource, metadata);
    List<Document> pieces = textSplitter.split(pages);
    List<Document> chunks = new ArrayList<>(pieces.size());
    for (int i = 0; i < pieces.size(); i++) {
      String id =
          UUID.nameUUIDFromBytes(
                  (metadata.get("source") + "#" + i).getBytes(StandardCharsets.UTF_8))
              .toString();
      chunks.add(new Document(id, pieces.get(i).getText(), pieces.get(i).getMetadata()));
    }
    return chunks;
  }

  /** 流式 SHA-256：大 PDF 不全量进内存。 */
  private String sha256(Resource resource) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (InputStream in = resource.getInputStream()) {
      byte[] buffer = new byte[8192];
      int read;
      while ((read = in.read(buffer)) != -1) {
        digest.update(buffer, 0, read);
      }
    }
    return toHex(digest.digest());
  }

  /**
   * 分块参数签名：参数元组的 SHA-256，入 manifest 顶层作判变键。 未来 splitter 新增参数（overlap 等）并入同一元组即可，格式不自描述（hash
   * 不可读属有意—— 参数值在 application.yml，签名只回答"变没变"）。
   */
  private String splitterSignature() {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return toHex(digest.digest(("chunk-size:" + chunkSize).getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 不可用", e);
    }
  }

  private static String toHex(byte[] bytes) {
    StringBuilder hex = new StringBuilder();
    for (byte b : bytes) {
      hex.append(String.format("%02x", b));
    }
    return hex.toString();
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
