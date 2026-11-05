# W30 #1 设计草案：授权服务器最小集（认证中心独立工程）

> 2026-11-05 起草（W30 #1 第一步：取证 + 设计先行，行为零变化）。
> 仓库结构裁决点（§二）在本文成文后请 Owner 裁决，结论回填「裁决记录」段。

## 一、取证记录（2026-11-05）

| 取证项 | 结果 |
|---|---|
| SAS 独立工件（`spring-security-oauth2-authorization-server`） | Maven Central 止于 1.5.1，无 2.x 线 |
| **Boot 4 时代授权服务器并入 Boot 自身** | `org.springframework.boot:spring-boot-starter-security-oauth2-authorization-server` **4.1.1 已在 Central 核实存在**，由 Boot 4.1.1 BOM 托管——与 W29 引入 security starter 同一姿势，无需手写版本 |
| 主工程数据库姿势 | 纯 JDBC（spring-ai chat memory repository），无 JPA/Flyway；schema 靠连接串 `createDatabaseIfNotExist=true` 兜底建库，凭据在 .env（A5） |
| 主工程结构 | 单 module Maven（根 pom 即应用 pom，无 `<modules>`），Jib 构建镜像 `ai-evolution:${version}` |

**结论**：认证中心同样用 Boot 4.1.1 + 官方授权服务器 starter + 纯 JDBC + `auth` 独立 schema
（同一 MySQL 容器，库级隔离）——技术栈与主工程完全对齐，不引入 JPA/Flyway 新技术。

## 二、仓库结构裁决点（O2 约束：一切开发在本仓库）

- **候选 A（倾向）：`auth-center/` 子目录独立 Maven 工程**——自带 pom（parent 同为
  Boot 4.1.1），不进根 pom reactor（根 pom 无 modules，天然不聚合）；CI 加第二路
  `verify`。主工程**零搬移**，git 历史不动；「独立部署单元 + 独立 pom」本身就是物理
  边界的表达；两工程不能互相 import 正是我们想要的效果。代价：两处 pom 的 Boot 版本
  靠纪律对齐（README 登记）。
- 候选 B：根 pom 改聚合（packaging=pom + modules），现有代码搬进 `app/` 子目录——
  教科书标准形态，但全仓搬移爆炸半径太大（git 历史 churn + CI/Jib/脚本/README 全改），
  违背 O3「5 分钟可 review」。
- 候选 C：独立仓库——违反 O2，排除。

## 三、部署形态与包结构（A9 子域分包）

- 独立 Spring Boot 应用，端口 **19000**（主应用 18080 不动）；镜像 `auth-center:${version}`（Jib）
- 包结构 `com.aievolution.authcenter` 下按子域：
  - `identity`：用户域——`UserAccount` 表（id/username/password_hash/role/status/created_at）、
    UserDetailsService 实现、AdminSeedRunner（env 注入 argon2 哈希、首启动幂等落库）
  - `protocol`：协议端点装配域——SAS SecurityFilterChain、双流程配置、JWT/JWKS
  - `clientregistry`：OAuth2 客户端注册表（配置化，yml/env 驱动）
  - `registration`：注册端点（#6 落位，本周先建包占位，#6 实现）
- 用户表含 `status` 字段（active/pending）——#6 注册的默认拒绝语义在表结构层预留

## 四、双流程设计

| 流程 | 服务对象 | 端点（SAS 自带） |
|---|---|---|
| 授权码 + PKCE | 人（对话页登录） | `/oauth2/authorize` + `/oauth2/token` + 登录页（formLogin 起步） |
| 客户端凭证 | 机器（MCP 客户端） | `/oauth2/token`（grant_type=client_credentials） |
| JWKS 公钥端点 | ai-evolution 资源服务器验签（#2） | `/.well-known/jwks.json` |

- 客户端注册表配置化（`auth.clients` 配置段）：`ai-evolution-web`（PKCE，public client）、
  `mcp-client`（客户端凭证，机密走 env）
- JWT 载荷含 scope（reader/writer/admin 映射）——#2 资源服务器直接消费
- 签名密钥：#1 开发态**启动时生成** RSA 对（重启旧令牌失效，开发可接受）；
  #4 落 env 注入 PEM + 初始密钥管理（轮转规程顺延 W31 在案）

## 五、测试策略

- 流程测试：Testcontainers MySQL 起 `auth` schema，客户端凭证流（curl 级）+ 授权码流
  （MockMvc 走 authorize→login→token 全链）跑通
- 种子 Runner 幂等性测试（二次启动不重复插入）
- 红队视角自测：错 client_secret / 未知 client_id / pending 用户登录被拒（#6 前可手工注库）

## 六、分步拆解（O3 小步，每步独立编译+测试绿）

1. **空壳工程**：`auth-center/` pom + Application + actuator 健康检查 + CI 第二路（零业务）
2. **机器通道先通**：SAS 最小配置 + 客户端注册表配置化 + 客户端凭证流测试
3. **人通道**：identity 域（用户表 + 种子 Runner）+ 授权码+PKCE 流测试
4. **收尾**：JWKS 端点核验 + 本草案裁决回填 + 计划打勾

## 裁决记录

**Owner 裁决（2026-11-05）：候选 A**——`auth-center/` 子目录独立 Maven 工程，主工程零搬移。
两处 pom 的 Boot 版本对齐纪律：升版本时两 pom 同步改，README 快照层登记双工程结构（#1 收官时落）。
