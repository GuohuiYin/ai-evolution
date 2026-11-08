# 值班操作卡（On-call Runbook）

> 建卡：W31 #5（2026-11-08）。定位：值守窗口与日常运维的「第一步翻这里」——
> 每章一个操作主题，步骤化、可照抄、带验证与回滚。新操作主题（如 S9 日志轮转纪律）
> 追加为新章节，不另起文件。维护纪律同 A8：操作面变化当周入卡。

## 章节索引

| § | 主题 | 建卡周 |
|---|---|---|
| 1 | 密钥轮转规程 | W31 |

---

## §1 密钥轮转规程（W31 #5）

### 密钥面总表

| 密钥 | 环境变量 | 用途 | 轮转影响面 | 建议周期/触发 |
|---|---|---|---|---|
| DeepSeek API Key | `AI_API_KEY` | 主力模型（对话/分析/judge） | 换钥瞬间旧 key 请求 401，failover 自动切备用臂兜住 | 疑似泄露立即；例行每季度 |
| SiliconFlow API Key | `SILICONFLOW_API_KEY` | embedding + 备用模型（Qwen） | embedding 与 failover 双链路同时失效（W26 P1 资金单点同账户） | 疑似泄露立即；例行每季度 |
| Tushare Token | `TUSHARE_TOKEN` | 行情真源（仅 `ai.stock.data-source=tushare` 启用时需要） | 行情工具调用失败，降级 mock | 疑似泄露立即 |
| JWT 签名私钥 | `AUTH_JWT_PRIVATE_KEY` | auth-center 签发 JWT 的 RS256 私钥 | **换钥即全部已签发 token 失效**（客户端重新登录/取 token 恢复） | 疑似泄露立即；例行每半年 |
| MCP 机器客户端密钥 | `AUTH_CLIENT_MCP_SECRET` | client_credentials 机器通道 | 换密钥后旧 secret 立即不可换 token；已签发 token 不受影响（验签只看公钥） | 疑似泄露立即；例行每半年 |
| admin 种子口令哈希 | `AUTH_ADMIN_PASSWORD_HASH` | auth-center admin 账号（仅用户表无 admin 时作种子） | 已入库的 admin 口令不受影响——改哈希后需同步改库内口令才生效（见 §1.6） | admin 口令疑似泄露立即 |
| 数据库口令 | `AUTH_DB_PASSWORD` / 主库 JDBC 口令 | MySQL 连接 | 换口令期间连接中断，重启对齐 | 例行每半年 |

### 通用纪律

1. 密钥值永不入库、永不进日志、永不贴聊天窗——规程里只写操作步骤不写值。
2. 新密钥先在供应商控制台**新建第二把**再吊销旧把（供应商支持多 key 并存时），
   避免「先吊销后更换」的硬中断窗口。
3. 本地态 = `.env`（gitignored）；集群态 = Secret `ai-evolution-secret`（命令式创建，永不入库）。
4. 每次轮转后在「轮转登记」小节记一行（日期/密钥/触发原因/操作人）——轮转频率本身是安全信号。

### §1.1 DeepSeek API Key（`AI_API_KEY`）

本地态：
```bash
# 1. platform.deepseek.com → API keys → 新建 key
# 2. 更新 .env 的 AI_API_KEY，重启主工程
set -a && source .env && set +a
./mvnw spring-boot:run   # 或重启既有进程
# 3. 验证：对话端点 200（未挂 Bearer 时 401 也证明服务活着）
curl -s -o /dev/null -w "%{http_code}" http://localhost:18080/actuator/health   # 期望 200
# 4. 确认无 failover 告警后，回控制台吊销旧 key
```
k8s 态：见 §1.8 统一 Secret 更新流程。
回滚：恢复 .env 旧值重启（若旧 key 未吊销）。

### §1.2 SiliconFlow API Key（`SILICONFLOW_API_KEY`）

同 §1.1 步骤，控制台为 cloud.siliconflow.cn。**注意双链路**：embedding 与备用模型共用此 key
（W26 P1：资金/密钥单点， embedding 与 failover 同账户）——换钥失败表现为检索退化为空路 +
failover 备用臂不可用，两症状同现即查此 key。

### §1.3 Tushare Token（`TUSHARE_TOKEN`）

tushare.pro 个人中心刷新 token → 更新 .env / Secret → 重启。验证：行情工具调用返回真数据
（数据源切 tushare 时）。低危：失败仅降级 mock，不阻断对话。

### §1.4 JWT 签名私钥（`AUTH_JWT_PRIVATE_KEY`）

现状两态（W30 #4）：
- **缺省（当前本地）**：不配置 = 每次启动临时生成（kid=ephemeral-*），重启即全量换钥、
  旧 token 全失效——开发姿态可接受， WARN 日志有声化。
- **持久通道**：配置 PKCS8 PEM 私钥（env 里换行用字面 `\n` 转义）——重启不换钥。

**重要约束：当前为单钥装载，无双钥并存（kid 双窗）能力**——换钥瞬间全部已签发 token 立即失效，
无过渡期。因此：
- 轮转选低峰窗口执行；换钥后人通道用户重新登录、机器客户端重新取 token 即恢复。
- 若未来需要零感轮转，先实现 JWKS 双钥并存（新 key 签发 + 旧 key 保留验签一个 TTL 周期），
  本规程随之更新。

生成与轮换：
```bash
# 1. 生成新 RSA 私钥并转 PKCS8 PEM
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out /tmp/jwt-new.pem
openssl pkcs8 -topk8 -nocrypt -in /tmp/jwt-new.pem -out /tmp/jwt-new-pkcs8.pem && rm /tmp/jwt-new.pem
# 2. 贴入 .env（换行转字面 \n）：AUTH_JWT_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----"
python3 -c "print(open('/tmp/jwt-new-pkcs8.pem').read().replace(chr(10),'\\\\n'))"
rm /tmp/jwt-new-pkcs8.pem   # 落地文件即用即焚
# 3. 重启 auth-center（19000），验证：
curl -s http://localhost:19000/oauth2/jwks | python3 -m json.tool   # kid 应变更为 env-injected
# 4. 换 token 验证全链（主工程 JWKS 验签自动取新公钥，无需重启主工程）
python3 scripts/auth_token.py  # 或走 client_credentials 流程
```

### §1.5 MCP 机器客户端密钥（`AUTH_CLIENT_MCP_SECRET`）

```bash
# 1. 生成新高熵密钥
openssl rand -hex 32
# 2. 更新 .env 的 AUTH_CLIENT_MCP_SECRET，重启 auth-center
# 3. 验证 client_credentials 换 token 成功：
set -a && source .env && set +a && python3 scripts/auth_token.py
```
已签发未过期 token 不受影响（验签只看 JWKS 公钥），仅新签发需新密钥——无硬中断。

### §1.6 admin 种子口令（`AUTH_ADMIN_PASSWORD_HASH`）

`AUTH_ADMIN_PASSWORD_HASH` 只在**用户表无 admin 时**作种子生效；admin 已入库后，
改口令走库内更新：
```bash
# 1. 生成新口令哈希（auth-center 自带 PasswordHashTool，吃明文参数打印 argon2 哈希；
#    注意口令会短暂出现在进程参数与 shell 历史——操作后清历史，或改用私密终端）
cd auth-center && ../mvnw -q org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.mainClass=com.aievolution.authcenter.identity.PasswordHashTool \
  -Dexec.args='新口令' && cd ..
# 2. 更新库内 admin 口令哈希
mysql -h 127.0.0.1 -P 3306 -u root -p auth -e \
  "UPDATE users SET password_hash='<新哈希>' WHERE username='admin';"
# 3. 更新 .env 的 AUTH_ADMIN_PASSWORD_HASH 保持一致（防 admin 被删后种子回落旧口令）
# 4. 验证：admin 新口令可登录认证中心
```

### §1.7 数据库口令（`AUTH_DB_PASSWORD` 与主库口令）

auth 库与主库同 MySQL 实例（库级隔离）。换口令：先改 MySQL 账户口令 → 同步更新两边
.env 对应变量 → 滚动重启 auth-center 与主工程。本地开发容器重建也可接受
（`docker compose` 容器数据卷保留）。

### §1.8 k8s 态 Secret 统一更新流程

```bash
# Secret 永不入库；从本地 .env 重新生成并原子替换（envFrom 整体注入）
set -a && source .env && set +a
kubectl create secret generic ai-evolution-secret \
  --from-literal=AI_API_KEY="$AI_API_KEY" \
  --from-literal=SILICONFLOW_API_KEY="$SILICONFLOW_API_KEY" \
  --from-literal=TUSHARE_TOKEN="$TUSHARE_TOKEN" \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl rollout restart deploy/ai-evolution   # envFrom 不感知 secret 变化，需滚动重启
kubectl rollout status deploy/ai-evolution
```
注：auth-center 尚未入集群（W31 现状），其密钥本地态先行；入集群时同法建
`auth-center-secret` 并补本节。

### 轮转登记

| 日期 | 密钥 | 触发 | 操作人 |
|---|---|---|---|
| （首行轮转时填） | | | |
