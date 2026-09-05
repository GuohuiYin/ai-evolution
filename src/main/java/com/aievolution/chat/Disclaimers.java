package com.aievolution.chat;

/**
 * 合规文案单点定义（约定 A12：红线逻辑单点化）。
 *
 * <p>免责声明是金融红线一的落地载体，全项目只此一份；任何端点需要附带免责声明时引用本类， 禁止复制后各自演化——审计时多处文案不一致即缺陷。
 */
public final class Disclaimers {

  private Disclaimers() {}

  /** 金融红线一：AI 生成内容的统一免责声明。 */
  public static final String AI_GENERATED = "——以上由 AI 生成，不构成投资建议。";
}
