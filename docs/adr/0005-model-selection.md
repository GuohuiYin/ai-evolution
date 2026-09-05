# ADR-0005：模型供应商选型与分工

- 状态：已接受
- 日期：2026-09-03（实测定稿，2026-09-05 补记）

## 背景

ADR-0001 约定"模型选型结论须有实测数据支撑"。W2-W3 完成实测（证据：`docs/model-selection-v1.md` 与 `docs/eval/model-selection-samples/` 原始样本）。

## 决策

1. **聊天模型：DeepSeek 主力 / Qwen 备选**——实测 10 题对比，DeepSeek 在金融事实题的红线遵循与结构化输出上更稳；`AI_MODEL` 固定为 `deepseek-v4-flash`。
2. **Embedding 模型：SiliconFlow `BAAI/bge-m3`**——DeepSeek 不提供 embedding API；bge-m3 中文金融语料表现好且 SiliconFlow 为 OpenAI 兼容协议，复用 `spring-ai-openai` 客户端。
3. **配置形态**：`spring.ai.model.chat=deepseek`、`spring.ai.model.embedding=openai`，双供应商共存于一个应用。

## 教训与坑（随 ADR 留痕）

- **别名漂移**：模型名以供应商控制台当前别名为准，凭记忆写名会 4xx——选型结论必须带实测时的确切模型名。
- **OpenAI 兼容客户端的 base-url 必须含 `/v1` 路径段**，否则 404。
- **402 余额不足**在 OpenAI 兼容通道抛的是客户端原生异常而非 Spring AI 包装异常——异常翻译需单独覆盖（见 `ChatExceptionHandler`）。

## 后果

- 正：聊天与向量化各自选了当前实测最优解；备选通道保留切换能力。
- 负：双供应商意味着两套密钥/余额管理；embedding 走云端带来数据出域考量（知识库为公开资料，可接受；涉敏数据时需重审本条）。
