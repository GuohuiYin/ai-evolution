package com.aievolution.chat;

import java.util.List;

/** 流式对话事件（W10 #0b）：SSE 通道上流动的两种事件。 sealed 层次——新增事件类型（如 ReAct 轨迹事件）必须显式 permits，编译器守住穷举。 */
public sealed interface ChatStreamPart permits ChatStreamPart.Delta, ChatStreamPart.Complete {

  /** 文本增量：模型输出的一个片段 */
  record Delta(String text) implements ChatStreamPart {}

  /** 收尾事件：会话标识与引用来源在流末尾一次性下发（免责声明已并入最后一个 Delta） */
  record Complete(String conversationId, List<SourceDocument> sources) implements ChatStreamPart {}
}
