#!/usr/bin/env python3
"""W26 #2 成本曲线：读 token 台账 JSONL，出「时间×token×成本」分面图。

两面板：① 累计 token 按通路（channel）分面；② 累计成本（¥）按模型分面。
计量与计价分离（CostEntry 只记 token）：单价表在本文件头部，溯源
docs/cost/w8-cost-report.md（DeepSeek 官方目录价 2026-09-09 口径，
缓存 miss ¥2/百万、hit ¥0.5/百万、输出 ¥8/百万）——调价只改本表，不改台账。
未在表内的模型（如 failover 备模型）只计 token 不计价，stdout 明示。

用法：
  python3 scripts/w26_cost_curve.py                          # 全量台账
  python3 scripts/w26_cost_curve.py --since-hours 3          # 最近 3 小时窗口
"""

import argparse
import json
import sys
from collections import defaultdict
from datetime import datetime, timedelta, timezone
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt

plt.rcParams["font.sans-serif"] = ["Hiragino Sans GB", "PingFang SC", "Noto Sans CJK SC", "Arial Unicode MS"]
plt.rcParams["axes.unicode_minus"] = False

# 单价表（元/百万 token）：溯源 docs/cost/w8-cost-report.md（DeepSeek 官方目录价
# 2026-09-09 口径：缓存 miss ¥2/百万、hit ¥0.5/百万、输出 ¥8/百万）；Pro 档按
# docs/eval/selection/agent-tier-v1.md「官方价目约为 Flash 的 12 倍档」估算——调价只改本表。
# 未在表内的模型（如 failover 备模型）只计 token 不计价，stdout 明示。
PRICE_PER_M = {
    "deepseek-flash": {"prompt_miss": 2.0, "prompt_hit": 0.5, "completion": 8.0},
    "deepseek-chat": {"prompt_miss": 2.0, "prompt_hit": 0.5, "completion": 8.0},
    "deepseek-v4-flash": {"prompt_miss": 2.0, "prompt_hit": 0.5, "completion": 8.0},
    "deepseek-v4-pro": {"prompt_miss": 24.0, "prompt_hit": 6.0, "completion": 96.0},
}


def load_entries(path: Path, since_hours: float) -> list[dict]:
    entries = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.strip():
            try:
                entries.append(json.loads(line))
            except json.JSONDecodeError:
                pass  # 半行残写跳过（与 TokenLedger 读侧同口径）
    if since_hours > 0:
        cutoff = datetime.now(timezone.utc) - timedelta(hours=since_hours)
        entries = [e for e in entries if datetime.fromisoformat(e["at"].replace("Z", "+00:00")) >= cutoff]
    entries.sort(key=lambda e: e["at"])
    return entries


def entry_cost(e: dict, unknown: set) -> float:
    price = PRICE_PER_M.get(e["model"])
    if not price:
        unknown.add(e["model"])
        return 0.0
    hit = e.get("cacheReadTokens") or 0
    miss = e["promptTokens"] - hit
    return (miss * price["prompt_miss"] + hit * price["prompt_hit"] + e["completionTokens"] * price["completion"]) / 1e6


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ledger", default="data/cost-ledger.jsonl")
    ap.add_argument("--since-hours", type=float, default=0, help="0=全量")
    ap.add_argument("--out", default="data/w26-traffic/cost-curve.png")
    args = ap.parse_args()

    entries = load_entries(Path(args.ledger), args.since_hours)
    if not entries:
        sys.exit("台账窗口内无记录")

    t0 = datetime.fromisoformat(entries[0]["at"].replace("Z", "+00:00"))
    cum_series_t = defaultdict(list)  # channel -> [(小时, 累计 token)]
    cum_series_c = defaultdict(list)  # model   -> [(小时, 累计成本)]
    totals_t, totals_c = defaultdict(int), defaultdict(float)
    unknown = set()

    for e in entries:
        h = (datetime.fromisoformat(e["at"].replace("Z", "+00:00")) - t0).total_seconds() / 3600
        totals_t[e["channel"]] += e["promptTokens"] + e["completionTokens"]
        cum_series_t[e["channel"]].append((h, totals_t[e["channel"]]))
        totals_c[e["model"]] += entry_cost(e, unknown)
        cum_series_c[e["model"]].append((h, totals_c[e["model"]]))

    fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(10, 8), sharex=True)
    for ch, pts in cum_series_t.items():
        ax1.plot([p[0] for p in pts], [p[1] for p in pts], label=ch)
    ax1.set_ylabel("累计 token")
    ax1.set_title("W26 运行成本曲线——累计 token（按通路分面）")
    ax1.legend()
    ax1.grid(alpha=0.3)
    for m, pts in cum_series_c.items():
        ax2.plot([p[0] for p in pts], [p[1] for p in pts], label=m)
    ax2.set_xlabel(f"小时（自 {t0.astimezone().strftime('%m-%d %H:%M')} 起）")
    ax2.set_ylabel("累计成本（¥）")
    ax2.set_title("累计成本（按模型分面；未计价模型只入 token 面板）")
    ax2.legend()
    ax2.grid(alpha=0.3)

    Path(args.out).parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(args.out, dpi=150, bbox_inches="tight")
    print(f"条目 {len(entries)}；总 token {sum(totals_t.values()):,}；总成本 ¥{sum(totals_c.values()):.4f}")
    if unknown:
        print(f"未计价模型（只计 token）: {sorted(unknown)}")
    print(f"图已出 {args.out}")


if __name__ == "__main__":
    main()
