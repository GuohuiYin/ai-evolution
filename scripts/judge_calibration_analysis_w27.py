#!/usr/bin/env python3
"""W27 Judge 校准复测 · 揭盲分析（Owner 打分交回后运行）

口径与 W19-W21 一致：精确一致率守线 ≥80%（主口径），±1 容差只披露。
差异：30 案 × 三维 = 90 格（W27 起抽样 10→30）；judge 分数直接取自
r3 全量批日志（W27 起不再单独跑 rescore 盲袋，日志即分数源）。

用法：python3 scripts/judge_calibration_analysis_w27.py
输出：控制台打印分析表（登记档由 Agent 另行撰写）。
"""

import json
import random
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "scripts"))
from gen_calibration_worksheet_w27 import (  # noqa: E402
    GOLDEN, LOG, SEED_AB, SEED_ORDER, stratified_sample)

WORKSHEET = ROOT / "docs/eval/generation/w27-judge-calibration-worksheet-owner.md"

SCORE_RE = re.compile(
    r"\*\*打分\*\*：accuracy `(\d)` ｜ compliance `(\d)` ｜ grounding `(\d)`")
NOTE_RE = re.compile(r"^备注：(.*)$", re.MULTILINE)
VERDICT_RE = re.compile(
    r"\*\*对比结论\*\*：甲更好 `([x✓√_ ])` ｜ 乙更好 `([x✓√_ ])` ｜ 相当 `([x✓√_ ])`")
JUDGE_RE = re.compile(
    r"GenerationEvalRunner : \[(?P<cat>[\w-]+)\]\[(?P<query>.+?)\] 得分="
    r"(?P<a>\d)/(?P<c>\d)/(?P<g>\d)（总 \d+）理由=(?P<reason>.*)")

DIMS = ["accuracy", "compliance", "grounding"]


def case_order(sample):
    """与生成器同源：抽样后另起同种子 Random 洗牌，案号(1-based) → 案。"""
    indices = list(range(len(sample)))
    random.Random(SEED_ORDER).shuffle(indices)
    return [sample[i] for i in indices]


def ab_first_is_model(n):
    """与生成器同源：按案号顺序逐案取一次随机（撰写顺序即取数顺序）。"""
    rng = random.Random(SEED_AB)
    return [rng.random() < 0.5 for _ in range(n)]


def load_judge_scores():
    """r3 批日志 → {(cat, query): {dim: 分, reason: str}}；同键多轮取首条（报告段口径）。"""
    scores = {}
    for line in LOG.read_text(encoding="utf-8").splitlines():
        m = JUDGE_RE.search(line)
        if not m:
            continue
        key = (m.group("cat"), m.group("query"))
        if key not in scores:
            scores[key] = {
                "accuracy": int(m.group("a")),
                "compliance": int(m.group("c")),
                "grounding": int(m.group("g")),
                "reason": m.group("reason").strip(),
            }
    return scores


def main():
    sample = stratified_sample(json.loads(GOLDEN.read_text(encoding="utf-8")))
    ordered = case_order(sample)
    ab = ab_first_is_model(len(ordered))
    judge_scores = load_judge_scores()

    text = WORKSHEET.read_text(encoding="utf-8")
    blocks = re.split(r"^## 案 \d+$", text, flags=re.MULTILINE)[1:]
    assert len(blocks) == 30, f"工作表案数异常：{len(blocks)}"

    rows = []
    for no, block in enumerate(blocks, 1):
        case = ordered[no - 1]
        key = (case["category"], case["query"])
        m = SCORE_RE.search(block)
        assert m, f"案 {no} 未找到打分行"
        owner = dict(zip(DIMS, map(int, m.groups())))
        judge = judge_scores.get(key)
        assert judge, f"案 {no} 日志中未找到 judge 分数：{key}"
        note = (NOTE_RE.search(block) or [None, ""])[1].strip("_ \n")
        v = VERDICT_RE.search(block)
        verdict = None
        if v:
            picked = [i for i, g in enumerate(v.groups()) if g.strip() in ("x", "✓", "√")]
            verdict = ["甲", "乙", "相当"][picked[0]] if picked else None
        rows.append({
            "no": no, "cat": case["category"], "query": case["query"],
            "owner": owner, "judge": judge, "note": note,
            "verdict": verdict, "model_side": "甲" if ab[no - 1] else "乙"})

    total = len(rows) * 3
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

    print(f"精确一致率：{exact}/{total} = {exact/total:.1%}（守线 ≥80%）")
    print(f"容差±1一致率：{tol1}/{total} = {tol1/total:.1%}")
    for d in DIMS:
        print(f"  {d}：{per_dim[d]}/{len(rows)} = {per_dim[d]/len(rows):.0%}")
    print(f"方向分布：Owner 高 {direction['owner高']} 格，judge 高 {direction['judge高']} 格")
    print(f"分歧案：{len(disagreements)}/{len(rows)}")
    print()
    for r in disagreements:
        o = "/".join(str(r["owner"][d]) for d in DIMS)
        j = "/".join(str(r["judge"][d]) for d in DIMS)
        print(f"案{r['no']:02d} [{r['cat']}][{r['query']}] owner={o} judge={j}")
        print(f"  owner备注：{r['note'] or '（无）'}")
        print(f"  judge理由：{r['judge']['reason']}")
    print()
    print("成对对比（被测回答所在侧已换算）：")
    for r in rows:
        v = r["verdict"] or "未填"
        meaning = {"甲": "被测更好" if r["model_side"] == "甲" else "参照更好",
                   "乙": "被测更好" if r["model_side"] == "乙" else "参照更好",
                   "相当": "相当", "未填": "未填"}.get(v, v)
        print(f"  案{r['no']:02d}：{v} → {meaning}")


if __name__ == "__main__":
    main()
