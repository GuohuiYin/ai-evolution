package com.aievolution.loop.protocol;

/**
 * 协议标记别名（W20 #1 方案 B：开闭原则扩展点）。**一个别名一个 Bean**——模型漂移出新的 标记写法时，新增一个实现类即可，既有代码零修改。
 *
 * <p>实现契约：
 *
 * <ul>
 *   <li>标 {@code @Component}，由 {@link ProtocolMarkerRegistry} 自动收集装配
 *   <li>javadoc 必须附首次观测证据（日志批次 / 评审文档链接）；纯防御性别名注明出处
 *   <li>别名是协议字面量，逐字匹配（装配器负责 {@code Pattern.quote} 与长度排序）
 * </ul>
 */
public interface ProtocolMarker {

  /** 该别名归一到哪个协议标记种类 */
  MarkerKind kind();

  /** 标记字面量（不含冒号，如 {@code "思考"}、{@code "Action Input"}） */
  String alias();
}
