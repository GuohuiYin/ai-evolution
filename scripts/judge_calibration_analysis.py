#!/usr/bin/env python3
"""W18 Judge 校准 · 一致率分析（方法论步骤 5，揭盲后运行）

输入：Owner 打分工作表（已填）+ 跑批保密日志（judge 分）。
复用 gen_calibration_worksheet 的日志解析与种子，保证案号映射一致。
输出：JSON 到 stdout（总体/分维一致率、±1 容差、分歧清单、甲乙交叉验证）。
"""

import json
import random
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "scripts"))
from gen_calibration_worksheet import (  # noqa: E402
    LOG, GOLDEN, SEED_ORDER, SEED_AB, parse_answers)

WORKSHEET = ROOT / "docs/eval/generation/w18-judge-calibration-worksheet-owner.md"

SCORE_RE = re.compile(r"accuracy `(\d)` ｜ compliance `(\d)` ｜ grounding `(\d)`")
JUDGE_RE = re.compile(
    r"GenerationEvalRunner : \[(?P<cat>[\w-]+)\]\[(?P<query>.+?)\] 得分="
    r"(?P<a>\d)/(?P<c>\d)/(?P<g>\d)（总 \d+）理由=(?P<reason>.*)$")


def parse_judge():
    """有序列表解析（query 在 factual/multi-turn 间有重复，不能按 query 建字典）。"""
    out = []
    for line in LOG.read_text(encoding="utf-8").splitlines():
        m = JUDGE_RE.search(line)
        if m:
            out.append({
                "category": m.group("cat"), "query": m.group("query"),
                "accuracy": int(m.group("a")), "compliance": int(m.group("c")),
                "grounding": int(m.group("g")), "reason": m.group("reason").strip()})
    return out


def parse_worksheet():
    """返回 {案号: {query, scores, pick, remark}}。query 取问题行或多轮末轮。"""
    text = WORKSHEET.read_text(encoding="utf-8")
    blocks = re.split(r"\n## 案 (\d+)\n", text)[1:]
    cases = {}
    for no, block in zip(blocks[::2], blocks[1::2]):
        qm = re.search(r"\*\*问题\*\*：(.+)", block)
        if qm:
            query = qm.group(1).strip()
        else:
            turns = re.findall(r"\*\*对话轮次\*\*：\n((?:- .+\n)+)", block)
            query = [t[2:].strip() for t in turns[0].splitlines()][-1]
        sm = SCORE_RE.search(block)
        pick = None
        for opt in ("甲更好", "乙更好", "相当"):
            if re.search(opt + r" `✓`", block):
                pick = opt
        rm = re.search(r"备注：(.+)", block)
        remark = rm.group(1).strip() if rm else ""
        cases[int(no)] = {
            "query": query,
            "scores": {"accuracy": int(sm.group(1)), "compliance": int(sm.group(2)),
                       "grounding": int(sm.group(3))},
            "pick": pick, "remark": "" if remark == "_" else remark}
    return cases


def main():
    golden = {c["query"]: c for c in json.loads(GOLDEN.read_text(encoding="utf-8"))}
    judge = parse_judge()
    owner = parse_worksheet()
    entries = parse_answers(LOG)
    assert len(judge) == 22 and len(owner) == 22 and len(entries) == 22, \
        (len(judge), len(owner), len(entries))

    # 复算案号→日志序位与甲乙身份（与生成脚本同种子同序）；
    # judge 列表与 entries 同为日志序，按下标一一对应（query 有重复，按序位对齐）
    order_rng = random.Random(SEED_ORDER)
    ab_rng = random.Random(SEED_AB)
    indices = list(range(len(entries)))
    order_rng.shuffle(indices)
    case_meta = {}
    for no, idx in enumerate(indices, 1):
        case_meta[no] = {"log_idx": idx,
                         "ab_first_is_model": ab_rng.random() < 0.5}

    dims = ["accuracy", "compliance", "grounding"]
    total = match = tol = 0
    per_dim = {d: [0, 0] for d in dims}  # [match, total]
    direction = {"owner_higher": 0, "judge_higher": 0}
    disagreements = []
    cross_check = []
    for no in sorted(owner):
        idx = case_meta[no]["log_idx"]
        q = entries[idx][0]
        assert owner[no]["query"] == q, f"案 {no} query 不一致"
        assert judge[idx]["query"] == q, f"案 {no} judge 序位错位"
        o, j = owner[no]["scores"], judge[idx]
        row = {"no": no, "query": q, "category": j["category"],
               "owner": o, "judge": {d: j[d] for d in dims},
               "judge_reason": j["reason"], "owner_remark": owner[no]["remark"],
               "diffs": []}
        for d in dims:
            total += 1
            per_dim[d][1] += 1
            if o[d] == j[d]:
                match += 1
                per_dim[d][0] += 1
            else:
                row["diffs"].append(d)
                if o[d] > j[d]:
                    direction["owner_higher"] += 1
                else:
                    direction["judge_higher"] += 1
            if abs(o[d] - j[d]) <= 1:
                tol += 1
        if row["diffs"]:
            disagreements.append(row)
        # 甲乙交叉：judge accuracy=2 但 Owner 判参照方更好 → 线索
        pick = owner[no]["pick"]
        ref_better = (pick == "乙更好") if case_meta[no]["ab_first_is_model"] \
            else (pick == "甲更好")
        if j["accuracy"] == 2 and ref_better:
            cross_check.append({"no": no, "query": q, "pick": pick})

    result = {
        "overall": {"match": match, "total": total, "rate": round(match / total, 4)},
        "tolerance_pm1": {"match": tol, "rate": round(tol / total, 4)},
        "per_dim": {d: {"match": m, "total": t, "rate": round(m / t, 4)}
                    for d, (m, t) in per_dim.items()},
        "direction": direction,
        "n_cases_with_diff": len(disagreements),
        "disagreements": disagreements,
        "cross_check_acc2_but_ref_better": cross_check,
    }
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
