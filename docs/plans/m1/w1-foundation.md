# W1 回顾：工程地基与云原生基座（已完成 ✅）

> 状态：2026-09-02 完成并验收。本文档为补录回顾。
> 对应 PPT：W1「L0 模型层 + 工程搭建」。

## 实际交付

| 交付物 | 说明 | Commit |
|---|---|---|
| 工作约定 | `AGENTS.md` v1.0：TDD、Clean Code、云原生第一天、金融三红线、5 分钟 review 颗粒度 | `3039c6f` |
| 技术栈选型 | ADR-0001：Spring Boot 4.1.1 / Java 25 / Spring AI 2.0.1 / Maven | `3039c6f` |
| 工程骨架 | `/actuator/health`、liveness/readiness 探针、优雅停机、CI 工作流、首版 K8s 清单 | `033eb73` |
| 镜像构建 | Buildpacks → Jib 切换（ADR-0002），镜像名规范 `ai-evolution:版本` | `f272cb5` `f855cd5` |

## 相对 PPT 的偏离（已转入 W4 补课）

- ❌ Tushare/东财 REST 直调 → 后置，归宿 W5 工具调用（mock 先行）
- ❌ DeepSeek/Qwen 模型选型笔记 → W4-Step4 补
- ❌ 手算 Token 账单 → W4-Step4 合并补
- ➕ 超前项：K8s 清单与探针在 W1 就位（PPT 原计划 W12 才部署）

## 验收记录

健康探针通过、CI 绿、Minikube 首部署成功（stub 响应版）。
