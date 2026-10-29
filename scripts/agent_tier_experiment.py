#!/usr/bin/env python3
"""W25 #3-ii · Agent 通路双档对照实验（裁决②：B 的实测数据作 A 的调参依据）

两臂同 10 案（planner-tier-v1 同族样本，跨实验可对照）：
  flash 臂：默认起服（不切档）；pro 臂：AI_MODEL_TIER_AGENT=deepseek-v4-pro
逐案记录：回复全文、端到端延迟、模型调用次数与 token（读台账增量——
#2 成本工程产物的首个消费场景），臂标签由 CLI 入参指定。

用法（应用已起服前提下）：
  set -a && source .env && set +a && \
  python3 scripts/agent_tier_experiment.py --arm flash --from 0 --to 5
产出：docs/eval/selection/agent-tier-v1-{arm}.json
"""

import argparse
import json
import os
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LEDGER = ROOT / "data/cost-ledger.jsonl"
OUT_DIR = ROOT / "docs/eval/selection"

# 与 planner-tier-v1 同 10 案：跨实验对照（那次的臂是 planner 角色，这次是整条 agent 通路）
SAMPLE = [
    "茅台 2025 年归母净利润是多少",
    "宁德时代 2024 年营业总收入是多少",
    "茅台 2024 年净利润和分红情况如何",
    "600519 2024 年营收多少？顺便查年报里的工艺描述",
    "宁德时代 2024 年归母净利润约多少，动力电池全球市占率多少",
    "中期分红算在里面吗",
    "它的净利润呢",
    "茅台冰淇淋业务的营收占比是多少",
    "宁德时代固态电池什么时候量产",
    "宁德时代的股票现在值得买入吗",
]


def chat(query, conversation_id, api_key, timeout=280):
    body = json.dumps(
        {"message": query, "conversationId": conversation_id}
    ).encode("utf-8")
    req = urllib.request.Request(
        "http://localhost:18080/ai/chat",
        data=body,
        headers={"Content-Type": "application/json", "X-API-Key": api_key},
    )
    t0 = time.time()
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        payload = json.loads(resp.read())
    return payload.get("reply", ""), time.time() - t0


def ledger_lines():
    if not LEDGER.exists():
        return []
    return [json.loads(x) for x in LEDGER.read_text(encoding="utf-8").splitlines() if x.strip()]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--arm", required=True, help="臂标签（flash / pro）")
    ap.add_argument("--from", dest="start", type=int, default=0)
    ap.add_argument("--to", dest="end", type=int, default=len(SAMPLE))
    args = ap.parse_args()
    api_key = os.environ["AI_EVOLUTION_API_KEY"]

    out_file = OUT_DIR / f"agent-tier-v1-{args.arm}.json"
    results = json.loads(out_file.read_text(encoding="utf-8")) if out_file.exists() else {}

    for i in range(args.start, min(args.end, len(SAMPLE))):
        q = SAMPLE[i]
        before = len(ledger_lines())
        # 多轮案（6/7）共享会话以保留指代上下文；其余逐案独立会话
        conv = f"tier-{args.arm}-mt" if i in (6, 7) else f"tier-{args.arm}-{i}"
        reply, elapsed = chat(q, conv, api_key)
        new_entries = ledger_lines()[before:]
        calls = len(new_entries)
        tin = sum(e["promptTokens"] for e in new_entries)
        tout = sum(e["completionTokens"] for e in new_entries)
        tcache = sum(e.get("cacheReadTokens") or 0 for e in new_entries)
        models = sorted({e["model"] for e in new_entries})
        results[q] = {
            "reply": reply,
            "elapsed_sec": round(elapsed, 2),
            "model_calls": calls,
            "prompt_tokens": tin,
            "completion_tokens": tout,
            "cache_read_tokens": tcache,
            "models_seen": models,
        }
        print(f"[{i}|{args.arm}] {elapsed:.1f}s calls={calls} in={tin} out={tout} models={models}")
        out_file.write_text(
            json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8"
        )

    print(f"written: {out_file.name}")


if __name__ == "__main__":
    main()
