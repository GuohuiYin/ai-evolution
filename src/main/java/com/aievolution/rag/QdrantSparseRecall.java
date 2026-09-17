package com.aievolution.rag;

import io.qdrant.client.ConditionFactory;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.WithPayloadSelectorFactory;
import io.qdrant.client.grpc.Collections.PayloadIndexParams;
import io.qdrant.client.grpc.Collections.TextIndexParams;
import io.qdrant.client.grpc.Collections.TokenizerType;
import io.qdrant.client.grpc.Common.Filter;
import io.qdrant.client.grpc.Points.CreateFieldIndexCollection;
import io.qdrant.client.grpc.Points.FieldType;
import io.qdrant.client.grpc.Points.RetrievedPoint;
import io.qdrant.client.grpc.Points.ScrollPoints;
import io.qdrant.client.grpc.Points.ScrollResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * {@link SparseRecall} 的 Qdrant 实现：payload 全文索引（{@code doc_content} 字段，multilingual 分词覆盖中文）+
 * MatchText 过滤召回。
 *
 * <p>诚实定位（ADR-0017 sparse 候选 B）：这是 BM25 的<strong>轻量近似</strong>而非等价物—— MatchText
 * 只有命中/不命中，没有词频-逆文档频率排序信号，召回内部排名按返回顺序（弱）。 选它的唯一理由是个位数文档的语料规模下自维护 IDF 成本收益不匹配；黄金集裁决不达标 直接升级候选 A（原生
 * BM25 稀疏向量），本接口不变。
 *
 * <p>韧性语义：sparse 是增强路而非必经路——gRPC 故障降级为空召回（dense 主路照常）， 与 W12 工具域"错误降级为观察不击穿"同一立场。
 *
 * <p>只在 {@code ai.rag.hybrid.enabled=true} 时装配；Bean 装配即确保全文索引存在（幂等）。
 */
@Component
@ConditionalOnProperty(name = "ai.rag.hybrid.enabled", havingValue = "true")
public class QdrantSparseRecall implements SparseRecall, SmartInitializingSingleton {

  private static final Logger log = LoggerFactory.getLogger(QdrantSparseRecall.class);

  // 与 QdrantVectorStore.DEFAULT_CONTENT_FIELD_NAME 同值（包私有不可引用，字面量对齐并注释锚定）
  private static final String CONTENT_FIELD = "doc_content";

  private final QdrantClient client;
  private final String collection;

  public QdrantSparseRecall(
      QdrantClient client,
      @Value("${spring.ai.vectorstore.qdrant.collection-name:knowledge_base}") String collection) {
    this.client = client;
    this.collection = collection;
  }

  @Override
  public void afterSingletonsInstantiated() {
    ensureIndex();
  }

  /**
   * 确保 {@code doc_content} 全文索引存在（幂等）。multilingual 分词器基于 charabia， 中文按字/词切分、数字串（12987 这类代号）整串成
   * token——正对字面匹配病灶。
   */
  void ensureIndex() {
    try {
      client
          .createPayloadIndexAsync(
              CreateFieldIndexCollection.newBuilder()
                  .setCollectionName(collection)
                  .setFieldName(CONTENT_FIELD)
                  .setFieldType(FieldType.FieldTypeText)
                  .setFieldIndexParams(
                      PayloadIndexParams.newBuilder()
                          .setTextIndexParams(
                              TextIndexParams.newBuilder()
                                  .setTokenizer(TokenizerType.Multilingual)
                                  .setLowercase(true)
                                  .build())
                          .build())
                  .setWait(true)
                  .build(),
              null)
          .get();
      log.info("sparse 召回路全文索引就绪：collection={} field={}", collection, CONTENT_FIELD);
    } catch (Exception e) {
      // 索引已存在属正常重入；其余异常降级处理（首次启动 collection 未建时摄入器会建）
      log.warn("全文索引创建未确认（可能已存在或 collection 未建）：{}", e.getMessage());
    }
  }

  // 候选网上限：本工程语料全库 chunks 量级小，50 已接近全捞；应用侧覆盖率裁决负责精度
  private static final int CANDIDATE_LIMIT = 50;
  // 候选网条件数上限：标识符 token + CJK 二元组，超长问句截断（黄金集最长问句 ~12 token）
  private static final int NET_CONDITION_LIMIT = 24;

  @Override
  public List<Document> recall(String query, KnowledgeFilter filter, int limit) {
    if (query == null || query.isBlank()) {
      return List.of();
    }
    // MatchText 只做候选网（宽捞）：严格 AND 无法服务自然语言问句（probe 实证），
    // 命中裁决由应用侧 SparseCoverage 按子串覆盖率完成——代号类查询的兜底也在那层
    String normalized = FullTextNormalizer.normalize(query);
    List<String> netTokens = netTokens(normalized);
    if (netTokens.isEmpty()) {
      return List.of();
    }
    Filter.Builder net = Filter.newBuilder();
    netTokens.forEach(t -> net.addShould(ConditionFactory.matchText(CONTENT_FIELD, t)));
    if (filter.docType() != null) {
      net.addMust(ConditionFactory.matchKeyword("docType", filter.docType()));
    }
    if (filter.asOf() != null) {
      net.addMust(ConditionFactory.matchKeyword("asOf", filter.asOf()));
    }
    try {
      ScrollResponse response =
          client
              .scrollAsync(
                  ScrollPoints.newBuilder()
                      .setCollectionName(collection)
                      .setFilter(net.build())
                      .setLimit(CANDIDATE_LIMIT)
                      .setWithPayload(WithPayloadSelectorFactory.enable(true))
                      .build())
              .get();
      List<Document> candidates = response.getResultList().stream().map(this::toDocument).toList();
      return SparseCoverage.rank(normalized, candidates, limit);
    } catch (Exception e) {
      // 增强路故障不击穿主路：降级为空召回，dense 结果照常服务
      log.warn("sparse 召回失败，降级为空路（dense 照常）：{}", e.getMessage());
      return List.of();
    }
  }

  /** 候选网 token：标识符整 token + CJK 二元组（整 CJK 长 token 的 AND 必败，probe 实证）。 */
  private static List<String> netTokens(String normalizedQuery) {
    List<String> tokens = SparseCoverage.queryTokens(normalizedQuery);
    List<String> net = new ArrayList<>();
    tokens.stream().filter(SparseCoverage::isIdentifier).forEach(net::add);
    net.addAll(
        SparseCoverage.cjkBigrams(
            tokens.stream().filter(t -> !SparseCoverage.isIdentifier(t)).toList()));
    return net.size() <= NET_CONDITION_LIMIT ? net : net.subList(0, NET_CONDITION_LIMIT);
  }

  private Document toDocument(RetrievedPoint point) {
    Map<String, Object> metadata = toPlainMap(point.getPayloadMap());
    String content = (String) metadata.remove(CONTENT_FIELD);
    return Document.builder().id(point.getId().getUuid()).text(content).metadata(metadata).build();
  }

  /** payload protobuf Value → Java 原生类型（只取本工程元数据用到的标量种类）。 */
  private static Map<String, Object> toPlainMap(
      Map<String, io.qdrant.client.grpc.JsonWithInt.Value> payload) {
    Map<String, Object> out = new HashMap<>();
    payload.forEach(
        (k, v) -> {
          switch (v.getKindCase()) {
            case STRING_VALUE -> out.put(k, v.getStringValue());
            case INTEGER_VALUE -> out.put(k, v.getIntegerValue());
            case DOUBLE_VALUE -> out.put(k, v.getDoubleValue());
            case BOOL_VALUE -> out.put(k, v.getBoolValue());
            default -> {
              /* NULL_/LIST_/STRUCT_VALUE 本工程元数据不涉及，跳过 */
            }
          }
        });
    return out;
  }
}
