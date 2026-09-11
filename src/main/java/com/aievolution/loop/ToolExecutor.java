package com.aievolution.loop;

/** Loop 的工具侧端口（A10 IOP）：按名执行工具，返回观察文本。桥接现有三工具在 #3 落地。 */
public interface ToolExecutor {

  String execute(String tool, String input);
}
