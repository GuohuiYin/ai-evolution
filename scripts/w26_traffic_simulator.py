#!/usr/bin/env python3
"""W26 #2 仿真流量驱动：黄金集打底回放 + 合成新题补密度（裁决①C 混合流量）。

设计要点：
- 全部请求打统一入口 /ai/chat，由服务端 ChatRouter 按内容自然分流 RAG/AGENT
  （含股票代码/取数关键词走 AGENT = pipeline 引擎 + HITL，真实过闸）。
- 会话化：约 40% 问题编入 2-3 轮会话（同一 conversationId + 通用追问模板），
  为多轮记忆与「会话深度分布」裁决（W26 #4）产出真实数据。
- 定速节奏：请求提交间隔均匀随机，3 线程池吸收 HITL 挂起（审批超时前请求会阻塞），
  单问阻塞不打断整体节奏。
- 熔断友好：503 等异常只记录不退出——熔断窗口的流量表现正是观测对象。
- 全量留痕：逐问 JSONL 日志（时间/会话/轮次/状态/延迟/回复头部），运行报告的数据源。

用法：
  python3 scripts/w26_traffic_simulator.py --minutes 180
  # 干跑验证（3 问即停）：
  python3 scripts/w26_traffic_simulator.py --max-requests 3 --min-interval 2 --max-interval 4
"""

import argparse
import json
import random
import threading
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from pathlib import Path

FOLLOW_UPS = [
    "它 2024 年的净利润是多少？",
    "那分红情况怎么样？",
    "和它的主要竞争对手相比呢？",
    "最近一期财报还有什么值得注意的？",
    "刚才说的这些，风险点是什么？",
]

lock = threading.Lock()
stats = {"ok": 0, "err": 0}


def load_queries(golden_path: Path, synthetic_path: Path | None) -> list[str]:
    golden = json.loads(golden_path.read_text(encoding="utf-8"))
    queries = [item["query"] for item in golden]
    if synthetic_path and synthetic_path.exists():
        synthetic = json.loads(synthetic_path.read_text(encoding="utf-8"))
        queries += [item["query"] for item in synthetic]
    return queries


def plan_sessions(queries: list[str], seed: int) -> list[list[str]]:
    """把问题编成会话：60% 单轮，40% 多轮（首轮为原问题，后续为通用追问）。"""
    rng = random.Random(seed)
    pool = queries[:]
    rng.shuffle(pool)
    sessions = []
    for q in pool:
        if rng.random() < 0.4:
            turns = [q] + rng.sample(FOLLOW_UPS, rng.choice([1, 2]))
        else:
            turns = [q]
        sessions.append(turns)
    return sessions


def post_chat(base_url: str, message: str, conversation_id: str) -> tuple[int, float, str]:
    payload = json.dumps({"message": message, "conversationId": conversation_id}).encode()
    req = urllib.request.Request(
        f"{base_url}/ai/chat", data=payload, headers={"Content-Type": "application/json"}
    )
    start = time.monotonic()
    try:
        with urllib.request.urlopen(req, timeout=900) as resp:  # HITL 挂起最长阻塞至超时
            body = json.loads(resp.read().decode())
            return resp.status, time.monotonic() - start, body.get("reply", "")[:120]
    except urllib.error.HTTPError as e:
        return e.code, time.monotonic() - start, f"HTTP {e.code}"
    except Exception as e:  # 连接拒绝/超时等——熔断与注入窗口的正常事件
        return -1, time.monotonic() - start, f"{type(e).__name__}: {e}"


def run_session(base_url, turns, out, run_id):
    conversation_id = f"w26-{run_id}-{int(time.time() * 1000)}-{random.randint(1000, 9999)}"
    for i, message in enumerate(turns, 1):
        status, latency, head = post_chat(base_url, message, conversation_id)
        record = {
            "ts": datetime.now(timezone.utc).isoformat(),
            "conversationId": conversation_id,
            "turn": i,
            "depth": len(turns),
            "query": message,
            "status": status,
            "latencyMs": int(latency * 1000),
            "replyHead": head,
        }
        with lock:
            out.write(json.dumps(record, ensure_ascii=False) + "\n")
            out.flush()
            stats["ok" if status == 200 else "err"] += 1


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base-url", default="http://localhost:18080")
    ap.add_argument("--golden", default="src/main/resources/eval/golden-set-generation.json")
    ap.add_argument("--synthetic", default="scripts/w26-synthetic-traffic.json")
    ap.add_argument("--minutes", type=float, default=180)
    ap.add_argument("--max-requests", type=int, default=0, help="0=不限，干跑用")
    ap.add_argument("--min-interval", type=float, default=25, help="请求提交间隔下限（秒）")
    ap.add_argument("--max-interval", type=float, default=55)
    ap.add_argument("--seed", type=int, default=20261030)
    args = ap.parse_args()

    queries = load_queries(Path(args.golden), Path(args.synthetic))
    sessions = plan_sessions(queries, args.seed)
    run_id = datetime.now().strftime("%Y%m%d-%H%M")
    out_dir = Path("data/w26-traffic")
    out_dir.mkdir(parents=True, exist_ok=True)
    out_path = out_dir / f"traffic-{run_id}.jsonl"

    print(f"题库 {len(queries)} 问 → {len(sessions)} 个会话；日志 {out_path}")
    rng = random.Random(args.seed + 1)
    submitted = 0
    with ThreadPoolExecutor(max_workers=3) as pool, out_path.open("w", encoding="utf-8") as out:
        out.write(json.dumps({"runId": run_id, "args": vars(args)}, ensure_ascii=False) + "\n")
        deadline = time.monotonic() + args.minutes * 60
        futures = []
        while time.monotonic() < deadline:
            for turns in sessions:
                if time.monotonic() >= deadline:
                    break
                futures.append(pool.submit(run_session, args.base_url, turns, out, run_id))
                submitted += len(turns)
                if args.max_requests and submitted >= args.max_requests:
                    break
                time.sleep(rng.uniform(args.min_interval, args.max_interval))
            if args.max_requests and submitted >= args.max_requests:
                break
        for f in futures:
            f.result()
    print(f"完成：提交 {submitted} 问；ok={stats['ok']} err={stats['err']}")


if __name__ == "__main__":
    main()
