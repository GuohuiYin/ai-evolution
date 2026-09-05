package com.aievolution.prompt;

/**
 * Prompt 资产库契约：prompt 是资产而非字符串常量——统一存 {@code resources/prompts/}， 文件名带版本号（如 {@code rag-chat-v1}），随
 * git 版本管理。
 *
 * <p>跨域协作以本接口为契约（约定 A10）：chat 与 analysis 域只依赖接口， 加载机制（classpath / 数据库 / 远程配置中心）由实现决定。
 */
public interface PromptLibrary {

  /**
   * 按名字加载 prompt 模板（不含 .md 后缀）。
   *
   * @throws IllegalStateException 模板不存在时快速失败——prompt 缺失是部署事故而非运行期容错场景
   */
  String get(String name);

  /** 判断指定名字的 prompt 模板是否存在（对外参数白名单校验用）。 */
  boolean exists(String name);
}
