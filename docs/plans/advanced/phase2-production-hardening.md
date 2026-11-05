# 二期（生产硬化期）一页纸

> 2026-11-05 成文（W28 任务 4）。输入：[W28+ 候选 TODO 总表](../../w28-plus-todo-candidates.md)
> 核销记录段 + Owner 2026-11-04 当日两轮裁决（W30 插入并扩容）。
> 定位：原 27 周蓝图已于 W27 完课（P5 四项硬件验收过，[w27-p5-acceptance.md](w27-p5-acceptance.md)），
> 二期不再横向扩能力面，而是把已有能力推向**生产级质量**——召回质量、Web 安全、
> 安全架构与认证授权、韧性，四条线各一周。

## 主题排序（Owner 已拍板）

| 周 | 主题 | 计划 | 排序依据与依赖 |
|---|---|---|---|
| W28 | **召回质量**：检索召回增强（案 30 归因驱动）+ rubric 维度澄清 + 消息长度上限 + 本页成文 | [w28-recall-enhancement.md](w28-recall-enhancement.md) | 案 30 实证在手，缺陷修复优先；顺手收两个小型项 |
| W29 | **Web 安全捆**：认证授权对齐 Spring Security 过滤器链+最小 RBAC（候选 #5）、安全响应头套件（#11）、CORS 显式策略（#12）、SSRF egress 控制（#13） | [w29-web-security-hardening.md](w29-web-security-hardening.md) | 四项同触动过滤器链，捆做一次到位，拆开会反复触碰同一批测试（O3）；依赖 W28 收官 |
| W30 | **系统安全架构设计 + 统一认证授权中心**：Spring Authorization Server 自建 IdP（框架级自建，不从零手写协议）；TLS 威胁模型声明入周 | [w30-unified-auth-center.md](w30-unified-auth-center.md) | Owner 2026-11-04 当日裁决插入并扩容（学习目标优先）；W29 的 Bearer/RBAC/scope 对齐为其预留最小迁移面 |
| W31 | **韧性捆**：failover 超时联动（#2）+ HITL 无人模式策略（#3），配值守窗口验收；安全事件审计流 + 密钥轮转规程并入 | [w31-resilience-hardening.md](w31-resilience-hardening.md) | 与下次值守窗口合并做（W26 运行报告遗留）；W30 扩容时顺延至此 |

## 取舍原则（二期通行）

- **O3 小步不混编**：每周一个主题，安全捆/韧性捆内部已是同域最小集合。
- **候选池唯一入口**：新发现缺口先登记 `docs/w28-plus-todo-candidates.md`，Owner 圈选后才转正式周计划；已裁决「不做」项（上下文压缩/记忆 L3-L5/rerank/bm25 等）带重评触发条件在案，不重复立案。
- **条件触发项不动手**：C2 语义缓存 / C3 影子评估 / C5 分布式限流等挂账项，触发条件不到不启。
