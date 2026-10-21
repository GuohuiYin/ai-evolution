#!/usr/bin/env python3
"""W20 Judge 校准重生 · 一致率分析（揭盲后运行；Owner 打分交回前勿动）

输入：Owner 已填工作表（w20-judge-calibration-worksheet-owner.md）+
跑批日志（w20-synthesis-v1-gen.log，judge 分）。
与 W18 版的差异：案号映射改为 query 直配（SAMPLE 内 query 唯一，种子复算甲乙身份），
不再依赖日志序位对齐——提取已切报告段口径（W20 #2①），序位假设随之消除。
输出：JSON 到 stdout（总体/分维一致率、±1 容差、分歧清单、甲乙交叉验证）。
"""

import json
import random
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "scripts"))
from gen_calibration_worksheet_w20 import (  # noqa: E402
    LOG, SEED_ORDER, SEED_AB, SAMPLE)

WORKSHEET = ROOT / "docs/eval/generation/w20-judge-calibration-worksheet-owner.md"

SCORE_RE = re.compile(r"accuracy `(\d)` ｜ compliance `(\d)` ｜ grounding `(\d)`")
JUDGE_RE = re.compile(
    r"GenerationEvalRunner : \[(?P<cat>[\w-]+)\]\[(?P<query>.+?)\] 得分="
    r"(?P<a>\d)/(?P<c>\d)/(?P<g>\d)（总 \d+）理由=(?P<reason>.*)$")


def parse_judge_by_query():
    """query → judge 分（重复 query 取首条，与生成脚本口径一致）。"""
    out = {}
    for line in LOG.read_text(encoding="utf-8").splitlines():
        m = JUDGE_RE.search(line)
        if m and m.group("query") not in out:
            out[m.group("query")] = {
                "category": m.group("cat"),
                "accuracy": int(m.group("a")), "compliance": int(m.group("c")),
                "grounding": int(m.group("g")), "reason": m.group("reason").strip()}
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
    judge_by_query = parse_judge_by_query()
    owner = parse_worksheet()
    assert len(owner) == len(SAMPLE), (len(owner), len(SAMPLE))

    # 复算案号→query 与甲乙身份（与生成脚本同种子同序）
    order_rng = random.Random(SEED_ORDER)
    ab_rng = random.Random(SEED_AB)
    indices = list(range(len(SAMPLE)))
    order_rng.shuffle(indices)
    case_meta = {}
    for no, idx in enumerate(indices, 1):
        case_meta[no] = {"query": SAMPLE[idx],
                         "ab_first_is_model": ab_rng.random() < 0.5}

    dims = ["accuracy", "compliance", "grounding"]
    total = match = tol = 0
    per_dim = {d: [0, 0] for d in dims}  # [match, total]
    direction = {"owner_higher": 0, "judge_higher": 0}
    disagreements = []
    cross_check = []
    for no in sorted(owner):
        q = case_meta[no]["query"]
        assert owner[no]["query"] == q, f"案 {no} query 不一致"
        j = judge_by_query.get(q)
        assert j, f"案 {no} judge 分缺失：{q}"
        o = owner[no]["scores"]
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
