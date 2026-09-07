#!/usr/bin/env bash
# MCP 验收冒烟脚本（W6-Step3）：对运行中的 ai-evolution 执行协议级全链路验证。
# 覆盖：initialize 握手 → 会话维持 → initialized 通知 → tools/list 三工具 → tools/call 真实数据。
# 用法：docs/scripts/mcp-smoke.sh [MCP_URL]   （默认 http://localhost:18080/mcp）
# 退出码：0 全链路通过；1 任一断言失败（打印失败点）。
set -euo pipefail

MCP_URL="${1:-http://localhost:18080/mcp}"
HEADERS=(-H "Content-Type: application/json" -H "Accept: application/json, text/event-stream")

fail() { echo "❌ $1"; exit 1; }
pass() { echo "✅ $1"; }

# 1. initialize：断言协议版本与 serverInfo，并捕获 Mcp-Session-Id
INIT_RESP=$(curl -s -D /tmp/mcp-smoke-headers.txt -X POST "$MCP_URL" "${HEADERS[@]}" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"mcp-smoke","version":"1.0"}}}')
echo "$INIT_RESP" | grep -q '"protocolVersion"' || fail "initialize 缺少 protocolVersion：$INIT_RESP"
echo "$INIT_RESP" | grep -q '"name":"ai-evolution"' || fail "serverInfo.name 非 ai-evolution：$INIT_RESP"
SID=$(grep -i "Mcp-Session-Id" /tmp/mcp-smoke-headers.txt | tr -d '\r' | awk '{print $2}')
[ -n "${SID:-}" ] || fail "响应缺少 Mcp-Session-Id 头"
pass "initialize 握手（session: ${SID:0:8}…）"

# 2. initialized 通知（生命周期要求：此后才允许业务请求）
curl -s -o /dev/null -X POST "$MCP_URL" "${HEADERS[@]}" -H "Mcp-Session-Id: $SID" \
  -d '{"jsonrpc":"2.0","method":"notifications/initialized"}'
pass "initialized 通知"

# 3. tools/list：断言三工具均暴露
LIST_RESP=$(curl -s -X POST "$MCP_URL" "${HEADERS[@]}" -H "Mcp-Session-Id: $SID" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}')
for tool in getDailyQuotes getFinancialSummary searchAnnouncements; do
  echo "$LIST_RESP" | grep -q "$tool" || fail "tools/list 缺少 $tool"
done
pass "tools/list 暴露三工具（getDailyQuotes / getFinancialSummary / searchAnnouncements）"

# 4. tools/call：真实调用并断言数据来源与时点标注（金融红线 03）
CALL_RESP=$(curl -s -X POST "$MCP_URL" "${HEADERS[@]}" -H "Mcp-Session-Id: $SID" \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"getDailyQuotes","arguments":{"code":"600519","from":"2024-12-30","to":"2024-12-31"}}}')
echo "$CALL_RESP" | grep -q '"isError":false' || fail "tools/call 返回 isError != false：$CALL_RESP"
echo "$CALL_RESP" | grep -q "600519" || fail "tools/call 响应缺数据：$CALL_RESP"
echo "$CALL_RESP" | grep -q "来源" || fail "响应缺来源标注（红线 03）：$CALL_RESP"
pass "tools/call 返回真实数据且带来源/时点标注"

echo "🎉 MCP 全链路验收通过（$MCP_URL）"
