# ai-evolution

> Java 架构师 → AI 应用架构师的实战演化项目：一个仓库贯穿 27 周，
> 从 `/ai/chat` 起步，到带护栏、可评估、走协议的生产形态 Agent 系统。

## 为什么做这个项目

- **干净的实验田**：从零搭建，每个技术选型都是显式决策，每个架构决策都能在 ADR 里讲出 why
- **项目制学习**：对照《Agent 工程化实战路线》课程地图，动手 > 阅读，每周有可演示产出物
- **可求职的作品集**：成品代码 + 架构图 + eval 报告三位一体

**业务域：金融个股研究**。选它不是偶然——金融合规约束（红线）是 Harness 工程最好的教材，
历史事实题天然适合建评估黄金集，研究循环天然是 Agent Loop。

### 三条设计红线（写进代码，全项目生效）

1. **免责声明强制附带**：所有输出末尾自动追加「不构成投资建议」
2. **禁止具体买卖建议**：拒答"该不该买/目标价多少"，引导回结构化事实与观点对照
3. **数字必须可溯源**：数据源 + 时点必带，模型不许凭记忆报数

## 技术选型

| 领域 | 选型 | 决策记录 |
|---|---|---|
| 语言 / 框架 | Java 25 (LTS) / Spring Boot 4.1.1 | [ADR-0001](docs/adr/0001-foundation-and-stack.md) |
| AI 编排 | Spring AI 2.0.1 | ADR-0001 |
| 模型供应商 | DeepSeek（对话）+ SiliconFlow bge-m3（Embedding，OpenAI 兼容协议） | [模型选型笔记 v1](docs/model-selection-v1.md)（双模型实测） |
| 向量数据库 | Qdrant v1.15.1 | W3 落地 |
| 镜像构建 | Jib | [ADR-0002](docs/adr/0002-image-build-with-jib.md) |
| 部署 | Kubernetes（本地 Minikube）——云原生第一天 | W2 落地 |
| 接口文档 | springdoc OpenAPI 3（本地开 UI / 集群关 UI，约定 A7） | W2 落地 |

## 快速上手

```bash
# 前置：.env 配置 AI_API_KEY / SILICONFLOW_API_KEY；本地 Qdrant 容器
docker run -d --name qdrant -p 6333:6333 -p 6334:6334 qdrant/qdrant:v1.15.1

# 本地运行（对话页 http://localhost:8080/，Swagger UI /swagger-ui/index.html）
set -a && source .env && set +a
./mvnw spring-boot:run

# 测试与格式
./mvnw verify            # 单测 + Testcontainers 集成测试（需 Docker）
./mvnw spotless:apply    # 提交前格式化

# Minikube 集群部署（详见 k8s/README.md）
DOCKER_HOST=unix:///var/run/docker.sock ./mvnw compile jib:dockerBuild -DskipTests
minikube image load ai-evolution:0.1.0-w1-SNAPSHOT
kubectl apply -f k8s/
```

## 架构快照（截至 W4 · 2026-09-04）

```
通路一（知识问答）                    通路二（结构化分析）
/ai/chat                            /ai/analyze?code=600519
   │                                   │
   ▼                                   ▼
RagChatService                     StockAnalysisService
（手动编排，非 Advisor 黑盒）         （拉数据 → 拼 prompt → .entity()）
   │                                   │
   │  bge-m3 向量化 ──► Qdrant          │  StockDataClient（mock 先行，W5 换真源）
   │  空检索 → 硬拒答                    │  未知代码 → 404（空数据不过模型）
   ▼                                   ▼
回答 + sources + 免责声明            StockAnalysisReport(JSON) + 免责声明

共享底座：PromptLibrary（prompts/*.md 版本化资产）· ChatExceptionHandler（502/503 语义化）
部署形态：ai-evolution + Qdrant 同集群（Minikube），ConfigMap/Secret 分层，
         探针 + 优雅停机 + Jib 镜像，eval harness（golden-set 21 条多源语义，Recall@5=86.7%，M1 门已过）
知识摄入：md + PDF（真实年报 303 分块），元数据三件套（source/docType/asOf）进 payload 支持过滤检索
```

**当前能力**：知识库问答可溯源、越界硬拒答、PDF 年报摄入与元数据过滤（"只查 2024 年报"）、Agent 工具调用（行情/财务/公告检索 + AOP 审计留痕）、结构化个股分析（CO-STAR + few-shot + schema 输出）、双模型供应商配置切换（DeepSeek 主力 / Qwen 备选，实测定稿）、集群内全链路运行。
**路线图**：W5 工具调用（@Tool）+ M1 验收门（Recall@5 ≥ 0.7）✅ → M2 MCP/护栏/Eval → M3 最小研究 Loop。

## 文档导航

| 目录 | 内容 |
|---|---|
| [docs/plans/](docs/plans/README.md) | 周作战计划与回顾（对 PPT 路线的执行校准版） |
| [docs/adr/](docs/adr/) | 架构决策记录 |
| [docs/eval/](docs/eval/) | 评估报告（prompt 对比 / 模型选型）与原始样本 |
| [docs/journal/](docs/journal/) | 周记（PPT 机制篇约定：每周末 5 行） |
| [docs/model-selection-v1.md](docs/model-selection-v1.md) | 模型选型笔记（DeepSeek vs Qwen 实测） |
| [k8s/](k8s/README.md) | 部署清单与配置分层原则 |
| [AGENTS.md](AGENTS.md) | 开发工作约定（TDD / Clean Code / 金融红线 / review 颗粒度） |

## 项目节奏

13 周实战（W1-W13，M1/M2/M3 验收门）+ 14 周进阶（W14-W27）。
每周产出可演示交付物；验收门不过不往下走——详细路线见 [docs/plans/](docs/plans/README.md)。
