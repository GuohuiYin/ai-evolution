package com.aievolution.chat;

/** 聊天能力的领域端口：对调用方屏蔽具体模型供应商。 新增模型（Qwen 等）或横切增强（缓存、限流）通过新增实现/装饰器完成，不改调用方。 */
public interface ChatService {

  String chat(String message);
}
