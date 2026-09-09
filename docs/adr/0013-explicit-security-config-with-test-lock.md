# ADR-0013：安全配置显式化 + 测试锁定（不吃库默认值）

## 状态

已接受（W8-1，2026-09-09）

## 背景

CVE-2026-59318（Spring AI 工具解析兜底可致提示注入越权）修复版 2.0.1 中，
`resolution.fallback.enabled` 库默认值已是 false（fail-fast）。只升级版本不写配置看似"已修复"，
但官方公告结论明确："升级本身不恢复边界，必须把兜底显式配成 fail-fast 才算修完"。

## 决策

安全相关的关键默认值一律在 `application.yml` **显式声明**，并配双断言测试锁定：

1. 配置必须显式存在（Environment 断言非 null——防"删了配置靠默认值"的静默退化）
2. 生效值必须等于安全值（防误开/未来版本默认值回翻）

本次落地：`spring.ai.tools.resolution.fallback.enabled=false` +
`spring.ai.tools.limits.max-total-tool-calls=10`（纵深防御，超限 RETURN_ERROR_RESPONSE 不击穿用户）。
测试：`ToolResolutionFailFastTest`。

## 替代方案与否决理由

| 方案 | 否决理由 |
|---|---|
| 只吃库默认值 | 未来版本为兼容可能回翻默认值，无任何信号 |
| 只写配置不写测试 | 配置可能被误删/误改，CI 无感 |

## 后果

- 安全红线从"库的善意"变成"我方的显式承诺 + CI 硬断言"
- 本原则适用于后续所有安全默认值（限流、超时、脱敏开关），新项沿用同模式
