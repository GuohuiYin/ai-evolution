#!/usr/bin/env python3
"""W21 Judge 校准复测 · 揭盲分析（Owner 打分交回后运行）

口径与 W19/W20 完全一致：同分层同种子 10 案 × 三维 = 30 格，精确一致率守线 ≥80%。
输入：w21-judge-calibration-worksheet-owner.md（Owner 已填）+ w21-judge-rescore.json（盲袋）。
输出：控制台打印分析表（登记档由 Agent 另行撰写）。

用法：python3 scripts/judge_calibration_analysis_w21.py
"""

import json
import random
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "scripts"))
from gen_calibration_worksheet_w21 import SAMPLE, SEED_AB, SEED_ORDER  # noqa: E402

WORKSHEET = ROOT / "docs/eval/generation/w21-judge-calibration-worksheet-owner.md"
RESCORE = ROOT / "docs/eval/generation/w21-judge-rescore.json"

SCORE_RE = re.compile(
    r"\*\*打分\*\*：accuracy `(\d)` ｜ compliance `(\d)` ｜ grounding `(\d)`")
CASE_RE = re.compile(r"^## 案 (\d+)$", re.MULTILINE)
NOTE_RE = re.compile(r"^备注：(.*)$", re.MULTILINE)
VERDICT_RE = re.compile(
    r"\*\*对比结论\*\*：甲更好 `([x_ ])` ｜ 乙更好 `([x_ ])` ｜ 相当 `([x_ ])`")

DIMS = ["accuracy", "compliance", "grounding"]


def case_order():
    indices = list(range(len(SAMPLE)))
    random.Random(SEED_ORDER).shuffle(indices)
    return [SAMPLE[i] for i in indices]  # 案号(1-based) → query


def ab_first_is_model():
    rng = random.Random(SEED_AB)
    return [rng.random() < 0.5 for _ in SAMPLE]


def main():
    text = WORKSHEET.read_text(encoding="utf-8")
    rescore = json.loads(RESCORE.read_text(encoding="utf-8"))
    queries = case_order()
    ab = ab_first_is_model()

    blocks = re.split(r"^## 案 \d+$", text, flags=re.MULTILINE)[1:]
    assert len(blocks) == 10, f"工作表案数异常：{len(blocks)}"

    rows = []
    for no, block in enumerate(blocks, 1):
        q = queries[no - 1]
        m = SCORE_RE.search(block)
        assert m, f"案 {no} 未找到打分行"
        owner = dict(zip(DIMS, map(int, m.groups())))
        judge = {d: rescore[q][d] for d in DIMS}
        note = (NOTE_RE.search(block) or [None, ""])[1].strip("_ \n")
        v = VERDICT_RE.search(block)
        verdict = None
        if v:
            picked = [i for i, g in enumerate(v.groups()) if g.strip() == "x"]
            verdict = ["甲", "乙", "相当"][picked[0]] if picked else None
        # 甲=被测回答 当 ab[no-1] 为真，否则甲=参照
        model_side = "甲" if ab[no - 1] else "乙"
        rows.append(
            {"no": no, "query": q, "owner": owner, "judge": judge,
             "note": note, "judge_reason": rescore[q]["reason"],
             "verdict": verdict, "model_side": model_side})

    exact = tol1 = 0
    per_dim = {d: 0 for d in DIMS}
    direction = {"owner高": 0, "judge高": 0}
    disagreements = []
    for r in rows:
        diff_flag = False
        for d in DIMS:
            o, j = r["owner"][d], r["judge"][d]
            if o == j:
                exact += 1
                per_dim[d] += 1
            else:
                diff_flag = True
                direction["owner高" if o > j else "judge高"] += 1
            if abs(o - j) <= 1:
                tol1 += 1
        if diff_flag:
            disagreements.append(r)

    print(f"精确一致率：{exact}/30 = {exact/30:.1%}（守线 ≥80%）")
    print(f"容差±1一致率：{tol1}/30 = {tol1/30:.1%}")
    for d in DIMS:
        print(f"  {d}：{per_dim[d]}/10 = {per_dim[d]/10:.0%}")
    print(f"方向分布：Owner 高 {direction['owner高']} 格，judge 高 {direction['judge高']} 格")
    print(f"分歧案：{len(disagreements)}/10")
    print()
    for r in disagreements:
        o = "/".join(str(r["owner"][d]) for d in DIMS)
        j = "/".join(str(r["judge"][d]) for d in DIMS)
        print(f"案{r['no']:02d} [{r['query']}] owner={o} judge={j}")
        print(f"  owner备注：{r['note'] or '（无）'}")
        print(f"  judge理由：{r['judge_reason']}")
    print()
    print("成对对比（model_side=被测回答所在侧）：")
    for r in rows:
        v = r["verdict"] or "未填"
        meaning = {"甲": "被测更好" if r["model_side"] == "甲" else "参照更好",
                   "乙": "被测更好" if r["model_side"] == "乙" else "参照更好",
                   "相当": "相当", "未填": "未填"}.get(v, v)
        print(f"  案{r['no']:02d}：{v} → {meaning}")


if __name__ == "__main__":
    main()
