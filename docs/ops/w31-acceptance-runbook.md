# W31 值守窗口验收执行单（Owner 在场，≥1h）

> 2026-11-08 成文。用途：验收 W31 任务 #1（failover 超时联动）+ #2（HITL 无人模式）+ #4（安全事件审计流）。
> 与 W26 窗口的差异：W26 考「我能否定位」，本次考「系统行为是否符合新设计」——
> 注入时刻双方对齐，不考定位计时。注入机制复用 W26 操作卡（hosts 断流法）。
> 退出口径：双故障注入实测通过（failover 换臂零 500、HITL 无人模式按策略执行）+ 审计流对照。

## 前置检查（窗口开始前 5 分钟）

```bash
# 1. 四容器在线
docker ps --format '{{.Names}}' | grep ai-evolution   # 期望 mysql/qdrant/temporal/phoenix
# 2. auth-center 在线
curl -s -o /dev/null -w "%{http_code}" http://localhost:19000/actuator/health   # 200
# 3. 主工程以 pipeline 引擎 + HITL 无人模式重启（W31 窗口专用配置）
set -a && source .env && set +a
AI_LOOP_ENGINE=pipeline AI_LOOP_HITL_MODE=unattended \
  PATH="$HOME/.local/bin:$PATH" ./mvnw spring-boot:run
# 4. 就绪 + 拿机器令牌
curl -s -o /dev/null -w "%{http_code}" http://localhost:18080/actuator/health   # 200
python3 scripts/auth_token.py > /tmp/w31-token.txt
```

## 窗口时间线

| 时刻 | 动作 | 验收断言 |
|---|---|---|
| T+0 | 基线：正常 pipeline 提问一笔（如「茅台 2024 营收多少」） | 200，作答正常；台账 byModel=DeepSeek |
| T+10 | **注入①：DeepSeek 断流**（hosts 法，命令见下） | — |
| T+12 | 再发同一问题 | **200**（Qwen 承接，允许慢至数分钟）；`logs/security-audit.log` 无异常；台账 byModel 出 Qwen；**无 StartToClose 500**（#1 核心断言） |
| T+40 | 恢复 hosts（命令见下） | 后续请求回主力臂（冷却窗 PT5M 过后） |
| T+45 | **注入②：HITL 触发**——无人模式下连问「它 2024 年的净利润是多少？」（W26 实测触发句） | 打回耗尽时**秒级拒答**（不再空转 10 分钟）；SSE 轨迹见 `unattended-refusal` 事件（#2 核心断言） |
| T+55 | **注入③：安全事件三类**——无凭证请求（401）/ 低权令牌调写端点（403）/ 一分钟内高频连发（429） | `logs/security-audit.log` 三类事件齐全且带 traceId，与业务日志分流（#4 核心断言） |
| T+60 | 收尾：恢复默认配置重启主工程 | 值守窗口运行报告回填本单证据表 |

## 注入命令（复用 W26 操作卡）

```bash
# 注入①：DeepSeek 断流
sudo sh -c 'echo "127.0.0.1 api.deepseek.com" >> /etc/hosts'
# 恢复
sudo sed -i '' '/^127\.0\.0\.1 api\.deepseek\.com$/d' /etc/hosts

# 注入③-401：无凭证
curl -s -o /dev/null -w "%{http_code}" http://localhost:18080/ai/cost/summary
# 注入③-403：reader 令牌调写端点（无 reader 客户端时可用「无 scope 令牌」代替——W31 冒烟实抓的零角色路径）
# 注入③-429：高频连发 70 笔（限流 60/分钟）
for i in $(seq 70); do curl -s -o /dev/null -w "%{http_code}\n" \
  -H "Authorization: Bearer $(cat /tmp/w31-token.txt)" http://localhost:18080/ai/cost/summary; done | sort | uniq -c
```

## HITL 自然触发的备选方案

打回耗尽是低频事件（W26 五连触发有运气成分）。若 T+45 起 20 分钟未触发：
临时建一个窗口专用「永远打回」的 reviewer prompt 文件（`src/main/resources/prompts/reviewer-always-reject.md`，
窗口结束即删、**不入库**——不入库不触发 A13 eval 门），重启时 `AI_LOOP_REVIEWER_PROMPT=reviewer-always-reject`
指向它，一笔即触发打回耗尽。此为「以故障注入主动制造，不等自然故障」的退出条件兑现路径。

## 证据记录表（运行报告回填）

| 注入 | 断言 | 实测 | 证据（日志/traceId/台账截图） | 判定 |
|---|---|---|---|---|
| ①断流换臂 | pipeline 请求 200 零 500 | | | |
| ①断流换臂 | 预算档换备用档（活动超时 >120s 未爆） | | | |
| ②无人模式 | 打回耗尽秒级拒答（<60s） | | | |
| ②无人模式 | hitlEvent 含 unattended-refusal | | | |
| ③审计流 | AUTH_FAILURE/AUTHZ_DENIED/RATE_LIMITED 三类齐全 | | | |
| ③审计流 | 事件带 traceId、与业务日志分流 | | | |
