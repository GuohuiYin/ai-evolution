#!/usr/bin/env python3
"""W24 #3 badcase 人工裁定台：👎 登记 → 人工裁定 → 回流黄金集候选区（裁决②A：不自动入集）。

用法：
  python3 scripts/badcase_review.py list              # 列出待裁定 badcase
  python3 scripts/badcase_review.py approve <id>      # 裁定有效 → 状态 APPROVED + 写入候选区
  python3 scripts/badcase_review.py reject <id>       # 裁定无效 → 状态 REJECTED（留痕不删）

候选区 docs/eval/generation/golden-candidates.json 仅暂存骨架（query/回答快照/裁定留痕），
正式入 golden-set-generation.json 仍需按造数规范补齐 category/expectedBehavior/referenceAnswer
并过全量回归——本脚本只做登记与流转，不替代造数。
"""

import json
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
STORE = ROOT / "data/feedback.jsonl"
CANDIDATES = ROOT / "docs/eval/generation/golden-candidates.json"


def load(path, default):
    return json.load(open(path, encoding="utf-8")) if path.exists() else default


def save(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")


def cmd_list():
    entries = load(STORE, [])
    pending = [e for e in entries if e["status"] == "PENDING_REVIEW"]
    if not pending:
        print("无待裁定 badcase")
        return
    for e in pending:
        print(f"[{e['id']}] {e['createdAt'][:10]} 会话={e['conversationId'][:8]}…")
        print(f"  问：{e['userMessage'][:80]}")
        print(f"  答：{e['assistantAnswer'][:80]}")
        if e.get("comment"):
            print(f"  反馈：{e['comment']}")
    print(f"共 {len(pending)} 条待裁定")


def cmd_verdict(fid, approved):
    entries = load(STORE, [])
    for e in entries:
        if e["id"] == fid:
            if e["status"] != "PENDING_REVIEW":
                sys.exit(f"[{fid}] 当前状态 {e['status']}，仅 PENDING_REVIEW 可裁定")
            e["status"] = "APPROVED" if approved else "REJECTED"
            save(STORE, entries)
            if approved:
                candidates = load(CANDIDATES, [])
                candidates.append(
                    {
                        "feedbackId": fid,
                        "query": e["userMessage"],
                        "badAnswer": e["assistantAnswer"],
                        "feedbackComment": e.get("comment") or "",
                        "approvedAt": datetime.now(timezone.utc).isoformat(),
                        "note": "待按造数规范补 category/expectedBehavior/referenceAnswer 后转正式黄金集",
                    }
                )
                save(CANDIDATES, candidates)
                print(f"[{fid}] 已裁定有效 → 候选区 {CANDIDATES}")
            else:
                print(f"[{fid}] 已裁定无效（留痕）")
            return
    sys.exit(f"未找到 badcase id={fid}")


if __name__ == "__main__":
    if len(sys.argv) < 2 or sys.argv[1] not in ("list", "approve", "reject"):
        sys.exit(__doc__)
    if sys.argv[1] == "list":
        cmd_list()
    else:
        if len(sys.argv) != 3:
            sys.exit(f"用法：{sys.argv[0]} {sys.argv[1]} <id>")
        cmd_verdict(sys.argv[2], sys.argv[1] == "approve")
