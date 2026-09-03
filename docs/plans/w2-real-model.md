# W2 回顾：真实模型接入与工程化配套（已完成 ✅）

> 状态：2026-09-02 完成并验收。本文档为补录回顾。
> 注意：PPT 的 W2 主题是「L1 Prompt 工程（P0 个股分析）」，实际执行偏离为工程化配套建设。
> L1 欠账已在对账后编入 W4 补课周，见 `w4-l1-prompt-engineering.md`。

## 实际交付

| 交付物 | 说明 | Commit |
|---|---|---|
| DeepSeek 真实模型接入 | Spring AI 2.0.1 `ChatClient`，`/ai/chat` 端点，`AI_API_KEY` 走 `.env` 注入 | `36776a7` |
| OpenAPI 3 接口文档 | springdoc 3.1.0；约定 A7：本地开 Swagger UI、K8s 关闭（文档页也是攻击面） | `52ce860` |
| VS Code 调试配置 | `envFile` 注入 `.env`，本地开发体验闭环 | `642be48` |
| 内置对话页面 | `/` 轻量聊天页，作为日常测试台 | `a09ddf3` |
| K8s 真实模型版部署 | Secret 命令式创建（永不入库）+ ConfigMap 分层配置，集群内冒烟通过 | `db84ceb` |

## 本周沉淀的约定

- **配置分层**：非敏感进 ConfigMap（入库），敏感凭据进 Secret（命令式，不入库）
- **A7 文档暴露策略**：Swagger UI 仅本地，集群保留 `/v3/api-docs` JSON
- 密钥管理：`.env` 本地唯一事实源，K8s Secret 从 `.env` 生成

## 验收记录

本地 + 集群双环境 `/ai/chat` 真实模型冒烟通过；Swagger 本地可用、集群 404。
