#!/usr/bin/env python3
"""Agent 轨迹评估指标批量采集（W13 #4，M3 验收门）。

口径（2026-09-14 定稿，docs/plans/m3/README.md 验收门）：
- 步数：一次运行中的 trajectory 事件数（= LoopResult.steps().size() 的 SSE 侧观测）
- 冗余调用：同 tool + 同归一化 input 的重复次数（归一化 = 去空白、小写）
- 收敛率：stopReason=FINAL_ANSWER 的占比；MAX_STEPS 兜底话术开头固定，据此区分

采集点：SSE /ai/chat/stream 的 trajectory 事件——与 LoopListener.onComplete 同一观测端口
（AgentChatService 接线），零入侵；research-trace 日志为旁证。

用例集：生成黄金集中"单轮且首句路由到 AGENT"的用例（路由规则复刻 ChatRouter：
6 位股票代码或取数关键词）。multi-turn 用例的轮次依赖会话上下文，不在本脚本口径内。

用法：
  set -a && source .env && set +a   # 需要 AI_EVOLUTION_API_KEY（W12 #2 鉴权头 X-API-Key）
  python3 docs/scripts/trajectory-metrics.py [BASE_URL] [GOLDEN_SET]
  默认 http://localhost:18080 与 src/main/resources/eval/golden-set-generation.json
退出码：0 = 收敛率 100% 且冗余调用 0；1 = 阈值未达或用例失败。
"""

import json
import os
import re
import sys
import urllib.request

MAX_STEPS_FALLBACK_PREFIX = "本次研究未能在限定的"
STOCK_CODE = re.compile(r"\b\d{6}\b")
AGENT_KEYWORDS = ["股价", "行情", "走势", "营收", "净利", "利润", "财务"]


def routes_to_agent(message: str) -> bool:
    return bool(STOCK_CODE.search(message)) or any(k in message for k in AGENT_KEYWORDS)


def normalize(text: str) -> str:
    return re.sub(r"\s+", "", text).lower()


def run_case(base_url: str, query: str, api_key: str) -> dict:
    body = json.dumps({"message": query}).encode()
    req = urllib.request.Request(
        f"{base_url}/ai/chat/stream",
        data=body,
        headers={
            "Content-Type": "application/json",
            "Accept": "text/event-stream",
            "X-API-Key": api_key,
        },
    )
    steps, answer = [], ""
    with urllib.request.urlopen(req, timeout=300) as resp:
        event = None
        for raw in resp:
            line = raw.decode("utf-8").rstrip("\n")
            if line.startswith("event:"):
                event = line[6:].strip()
            elif line.startswith("data:"):
                data = line[5:].strip()
                if event == "trajectory":
                    steps.append(json.loads(data))
                elif event == "delta":
                    answer += data
    calls = [(s.get("tool", ""), normalize(s.get("input", ""))) for s in steps]
    redundant = len(calls) - len(set(calls))
    converged = bool(answer) and not answer.startswith(MAX_STEPS_FALLBACK_PREFIX)
    return {
        "steps": len(steps),
        "redundant": redundant,
        "stopReason": "FINAL_ANSWER" if converged else "MAX_STEPS",
        "tools": [c[0] for c in calls],
    }


def main() -> int:
    base_url = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:18080"
    golden = (
        sys.argv[2]
        if len(sys.argv) > 2
        else "src/main/resources/eval/golden-set-generation.json"
    )
    with open(golden, encoding="utf-8") as f:
        cases = json.load(f)
    selected = [c for c in cases if "turns" not in c and routes_to_agent(c["query"])]
    api_key = os.environ.get("AI_EVOLUTION_API_KEY", "")
    if not api_key:
        print("❌ 缺 AI_EVOLUTION_API_KEY（W12 #2 起全端点鉴权，请先 source .env）")
        return 1
    print(f"agent 路由单轮用例 {len(selected)} 条（黄金集共 {len(cases)} 条）\n")
    print(f"{'查询':42s} {'步数':>4s} {'冗余':>4s} stopReason     工具序列")
    failures, total_redundant, converged = 0, 0, 0
    for c in selected:
        q = c["query"]
        try:
            r = run_case(base_url, q, api_key)
        except Exception as e:  # noqa: BLE001 - 采集失败按未收敛计，不中断整批
            print(f"{q[:40]:42s}    -    - ERROR: {e}")
            failures += 1
            continue
        converged += r["stopReason"] == "FINAL_ANSWER"
        total_redundant += r["redundant"]
        print(
            f"{q[:40]:42s} {r['steps']:>4d} {r['redundant']:>4d} "
            f"{r['stopReason']:13s} {' → '.join(r['tools'])}"
        )
    n = len(selected)
    rate = converged / n if n else 0.0
    print(f"\n收敛率 {converged}/{n}（{rate:.0%}）｜冗余调用合计 {total_redundant}｜异常 {failures}")
    ok = failures == 0 and converged == n and total_redundant == 0
    print("阈值判定：收敛率=100% 且冗余=0 → " + ("✅ 达标" if ok else "❌ 未达标"))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
