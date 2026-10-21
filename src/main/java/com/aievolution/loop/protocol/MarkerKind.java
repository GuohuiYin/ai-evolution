package com.aievolution.loop.protocol;

/** ReAct 协议标记种类（规范四元）。解析结果以枚举为键，告别字符串字面量查找。 */
public enum MarkerKind {
  THOUGHT,
  ACTION,
  ACTION_INPUT,
  FINAL_ANSWER
}
