# AGENTS.md — ai_evolution 工作约定

> 本文件是本仓库的最高协作规范，人与 AI 协作者共同遵守。
> 制定日期：2026-09-02 · 版本 v1.1（2026-09-02 新增 A7，修订 C1） · 修改需经仓库所有者确认

## 0. 项目定位

ai_evolution：Java 架构师向 AI 应用架构师转型的实战项目。
业务域：个股研究（订单/商品/库存/持仓快照/自选池为统一素材）。
方法论：L0-L4 分层工程化（Prompt → Context → Harness → Loop），eval 驱动，动手 > 阅读。

## 1. 所有者约定（Owner Rules, O1-O8）

- **O1** 代码托管 GitHub，仓库根目录：`/Users/guohui/Workspace/ai-evolution`
- **O2** 一切开发工作在本仓库进行
- **O3** 分步推进：每步核心变更 ≤150 行、可独立编译、测试独立通过，Owner 约 5 分钟可 review 完
- **O4** Clean Code + TDD；识别并应用合适的设计模式（策略、模板方法等），遵循开闭原则；不为用模式而用模式。LLM 相关逻辑以黄金集 eval 作为回归测试（Agent 工程的 TDD 形态）
- **O5** Maven 依赖在满足兼容性前提下使用最新稳定版；引入前核对兼容矩阵，提交说明写明选型理由
- **O6** 使用 Java 25 LTS；新特性以可读性为先自然采用，不炫技
- **O7** 意图不明时先提问对齐，反复确认后再动手；宁可多问一轮，不做错方向
- **O8** 每个任务必须有达成目标的退出条件；Owner 未给出时，协作者必须主动询问或自拟后请 Owner 确认

## 2. 工程实践（Engineering Practices, A1-A6 立即执行 / B1-B4 按阶段引入）

- **A1** Maven Wrapper 入仓 + GitHub Actions CI（每次推送跑 `./mvnw verify`）
- **A2** Conventional Commits + 小步提交（feat/fix/refactor/test/docs/chore 前缀）
- **A3** Spotless 自动格式化，verify 阶段强制校验
- **A4** ADR 架构决策记录，存 `docs/adr/`，关键决策必须留痕
- **A5** 密钥与配置隔离（12-factor）：环境变量注入，`.env` 不入库，提供 `.env.example`
- **A6** 结构化日志 + Micrometer 埋点从第一个端点开始（Token 计量为交付物之一）
- **A7** API 文档即契约：所有 HTTP 接口必须有完整的 OpenAPI 3 文档（springdoc 注解生成，不手写 YAML）——每个端点标注 summary/description、请求与响应 schema、全部错误码（400/502/503 等）及示例；文档与实现同步变更。Swagger UI 仅本地开放，K8s 部署通过配置关闭 UI（文档页面也是攻击面）
- **B1** Testcontainers 集成测试——W3 引入 Qdrant 时启用
- **B2** ArchUnit 架构约束测试——W5 Harness 阶段启用，将依赖方向写成会失败的测试
- **B3** 结构化错误返回 RFC 7807 ProblemDetail——W5 工具工程启用
- **B4** AssertJ 断言 + 测试金字塔——W2 起：单测为主、集测守关键链路、eval 守 LLM 输出

## 3. 云原生约定（C1）

- 容器化从 W1 开始，使用 Jib 构建镜像，不手写 Dockerfile（原约定 Buildpacks 因网络约束替换，见 ADR-0002）
- 从第一个端点具备 K8s 友好最小集：actuator 健康探针（liveness/readiness 分离）、优雅停机、日志到 stdout、配置外置
- 部署清单存 `k8s/` 目录，基础设施即代码；镜像用语义化 tag，禁用 `latest`
- **开发循环本地化，部署验证里程碑化**：日常开发走 `./mvnw` 本地反馈环；每个里程碑与每周五在 minikube 做部署冒烟，保持"随时可部署"

## 4. Definition of Done

每步完成 = ① 编译绿 ② 测试绿（`./mvnw verify`）③ Owner review 通过 ④ 退出条件达成 ⑤ 含接口变更的步骤，OpenAPI 文档可访问且与实际行为一致。
五者齐备才算完成，协作者不得自行宣布完成。

## 5. 金融域三条红线（写进 Harness，全项目生效）

1. 所有输出末尾自动追加「不构成投资建议」
2. 禁止具体买卖建议——拒答"该不该买/目标价多少"，引导回结构化事实与观点对照
3. 数字必须可溯源——数据源 + 时点必带，模型不许凭记忆报数
