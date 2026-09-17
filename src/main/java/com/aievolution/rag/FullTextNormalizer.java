package com.aievolution.rag;

/**
 * W13 #2b 全文检索规整化：ASCII 字母数字串与相邻 CJK 之间插入空格。
 *
 * <p>为什么存在（probe 实证 2026-09-17）：Qdrant multilingual 分词器（charabia）把紧贴中文的 数字/字母粘连成一个 token（"12987流程"
 * 不可分），导致 "12987" 这类代号查询永远不中—— 这正是 W8/W13 #1 实证的头号失败簇。摄入与查询两侧应用同一规整化后， "遵循12987流程" → "遵循 12987
 * 流程"，分词器即可独立切出代号 token。
 *
 * <p>作用于摄入文本本身（单一副本，不留孪生字段）：对 dense embedding 与 prompt 展示的影响 可忽略（空格不改变语义），换得 sparse 路零额外存储。
 *
 * <p>已知残余限制：规整化后纯数字单 token 查询（如孤立的 "12987"，无其他词）仍不中—— multilingual 分词器对数字单 token 查询的处理缺陷（probe
 * 实证）；黄金集无此用例， 真实问句几乎不单独发代号。若未来成为病灶，升级路径是 ADR-0017 候选 A（真 BM25）。
 */
public final class FullTextNormalizer {

  /** 规整化版本号：规则变更须递增——该版本并入摄入 manifest 判变键，触发全量重建。 */
  public static final String VERSION = "v1";

  private FullTextNormalizer() {}

  public static String normalize(String text) {
    if (text == null || text.isEmpty()) {
      return text;
    }
    return text.replaceAll("([\\p{IsHan}])([A-Za-z0-9%])", "$1 $2")
        .replaceAll("([A-Za-z0-9%])([\\p{IsHan}])", "$1 $2");
  }
}
