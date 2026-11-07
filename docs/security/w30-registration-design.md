# W30 #6 设计草案：用户注册 + 审批激活

> 2026-11-07 草案。Owner 裁决已在案（2026-11-05）：注册纳入本周 + 候选 2（注册即 pending +
> 复用人工审批思想激活）+ argon2 哈希落库明文不过夜 + admin 只能来自 env 种子（注册产不出 admin）。
> 本草案只定实现形态，不重开已裁决项。

## 现状积木（#1 已就位，#6 只补缺口）

- `users` 表：`status` 列已预留 PENDING（默认）/ACTIVE；argon2 `PasswordEncoder` bean 已就位
- `UserAccountDetailsService`：PENDING → disabled，**pending 登录拒绝语义已在表结构层生效**
- 缺：注册入口、审批通道、注册限流

## 设计

### 1. 注册页（auth-center 自托管，permitAll）

- `GET /register` 极简表单（用户名/密码，与 SAS 登录页同风格裸 HTML）；`POST /register` 提交
- 校验：用户名 `^[a-zA-Z0-9_]{3,32}$`，密码 ≥ 8 位；失败重渲染表单带错误提示
- 入库：argon2 哈希（复用现有 PasswordEncoder），`role=READER`（最小权限，提权走 DBA 动作显式记录），
  `status=PENDING`（表默认值）；**明文不过夜**——入参即哈希，不落日志不落库
- 防用户名枚举：用户名已存在与注册成功给**同一文案**「已提交，待管理员审批」（不透露占用与否）
- 成功页引导：审批通过后即可回对话页（18080）走登录流

### 2. 审批通道：admin 审批页（会话形态，修正草案前倾向）

**裁决点**：开工前提的倾向是「JWT 保护的 admin API」；实现形态修正为**审批页走链 2 formLogin
会话**（`GET /admin/users` 列 pending 清单 + 每行激活按钮 `POST /admin/users/{name}/activate`）：

- 审批动作本体就是人在浏览器点一下——会话形态最直接，不为中心引入第二套资源服务器装配；
  CSRF 由链 2 默认提供（表单注入 token），不额外开窗
- `hasRole("ADMIN")` 保护：admin 身份来自 env 种子（唯一来源不变）
- JWT admin API 留触发条件：出现自动化/批量审批诉求再加，不为假设需求铺面

### 3. 注册限流（防批量注册）

- 每 IP 5 次/小时（内存固定窗口，配置项 `auth.registration.rate-limit-per-hour`，A11 策略配置化）
- 超限 429 + 提示；只卡 POST /register，登录与审批不受影响

### 4. 端到端旅程

```
访客 → 19000/register 提交 → users 表 PENDING（argon2）
     → 尝试登录 → 拒绝（disabled）→ 告知 Owner
Owner → 19000/login（admin）→ /admin/users 看到 pending → 点激活 → ACTIVE
访客 → 18080 对话页 → 跳转登录 → 成功（role=reader）
```

## 测试（RegistrationFlowTest，全链全真）

1. 注册 → 库中 PENDING + 哈希非明文（`{argon2}` 前缀）
2. pending 登录被拒（formLogin 失败重定向）
3. admin 会话审批激活 → 库中 ACTIVE
4. 激活后登录通过（携带角色进授权码流的既有断言已由 AuthorizationCodeFlowTest 覆盖）
5. 限流：第 6 次注册 429
6. 枚举防护：已存在用户名注册返回与成功相同文案

## 不做（why-not，触发条件显式）

| 不做项 | 理由 / 触发条件 |
|---|---|
| 邮件验证 / 验证码 | 本地单 Owner 无 SMTP；对外开放时重评 |
| 自助改密/找回 | 单 Owner 场景 Owner 即管理员，改库即可；多用户真实使用时重评 |
| JWT admin API | 无自动化审批诉求；出现时再加 |
| 注册即 writer | 最小权限原则；提权是显式管理动作 |
