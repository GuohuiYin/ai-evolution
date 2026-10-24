#!/usr/bin/env python3
"""W22 #1 验收：新旧引擎 57 案对照报告生成。

解析两份跑批日志（GenerationEvalRunner 的输出格式）：
  [类别][问题] 得分=a/b/c（总 t）理由=...
注意：黄金集内同一问题可重复出现（factual 单轮 + multi-turn 带历史各一次），
按"日志出现顺序 = 黄金集顺序"逐次配对，不按问题文本去重。

用法：python3 scripts/eval_dual_arm_report.py <inline臂.log> <pipeline臂.log> <输出.md>
"""

import json
import re
import sys
from collections import defaultdict, deque

LINE = re.compile(r"\[([^\]]+)\]\[([^\]]+)\] 得分=(\d)/(\d)/(\d)（总 (\d+)）")
GOLDEN = "src/main/resources/eval/golden-set-generation.json"


def parse(path):
    """日志按出现顺序提取 {query: [score, ...]}。"""
    by_query = defaultdict(list)
    for line in open(path, encoding="utf-8", errors="replace"):
        m = LINE.search(line)
        if m:
            _cat, query, a, c, g, t = m.groups()
            by_query[query].append(
                {"accuracy": int(a), "compliance": int(c), "grounding": int(g), "total": int(t)}
            )
    return by_query


def align(golden, scores_by_query):
    """按黄金集顺序取每个问题的第 N 次出现，返回与黄金集等长的结果列表（缺案为 None）。"""
    cursors = defaultdict(deque)
    for q, scores in scores_by_query.items():
        cursors[q] = deque(scores)
    out = []
    for case in golden:
        dq = cursors[case["query"]]
        out.append(dq.popleft() if dq else None)
    return out


def main():
    golden = json.load(open(GOLDEN, encoding="utf-8"))
    arm_a = align(golden, parse(sys.argv[1]))  # inline 单循环
    arm_b = align(golden, parse(sys.argv[2]))  # pipeline 三角色
    out_path = sys.argv[3]

    missing_a = sum(1 for s in arm_a if s is None)
    missing_b = sum(1 for s in arm_b if s is None)

    cats = defaultdict(lambda: {"a": [], "b": []})
    red_a = red_b = red_n = 0
    diffs = []
    for case, sa, sb in zip(golden, arm_a, arm_b):
        if sa is None or sb is None:
            continue
        cats[case["category"]]["a"].append(sa["total"])
        cats[case["category"]]["b"].append(sb["total"])
        if case["category"] == "redline":
            red_n += 1
            red_a += sa["compliance"]
            red_b += sb["compliance"]
        d = sb["total"] - sa["total"]
        if abs(d) >= 2:
            diffs.append((case["query"], case["category"], d))

    n = sum(len(v["a"]) for v in cats.values())
    oa = sum(s for v in cats.values() for s in v["a"]) / n
    ob = sum(s for v in cats.values() for s in v["b"]) / n

    lines = []
    lines.append("# W22 #1 验收：三角色管线 vs 单循环 57 案对照\n")
    lines.append("> 2026-10-24。同一黄金集 57 案、同一 judge 与 rubric，两臂仅引擎不同。")
    lines.append("> 日志：logs/w22-inline-arm.log（A 旧单循环）/ logs/w22-pipeline-arm.log（B 三角色管线）。\n")
    lines.append(f"- 配对成功 {n}/57 案（A 臂缺 {missing_a}，B 臂缺 {missing_b}）\n")
    lines.append("## 总体与分类别均分（/6）\n")
    lines.append("| 类别 | 案数 | A 单循环 | B 三角色 | 差 |")
    lines.append("|---|---|---|---|---|")
    for c in sorted(cats):
        a = sum(cats[c]["a"]) / len(cats[c]["a"])
        b = sum(cats[c]["b"]) / len(cats[c]["b"])
        lines.append(f"| {c} | {len(cats[c]['a'])} | {a:.2f} | {b:.2f} | {b - a:+.2f} |")
    lines.append(f"| **总体** | {n} | **{oa:.2f}** | **{ob:.2f}** | **{ob - oa:+.2f}** |")
    lines.append("")
    lines.append(f"## 红线守线（compliance 维，满分 {2 * red_n}）\n")
    lines.append(f"- A 臂 {red_a}/{2 * red_n}，B 臂 {red_b}/{2 * red_n}\n")
    lines.append(f"## 逐案分差：|Δ|≥2 的案 {len(diffs)}/{n}\n")
    for q, c, d in sorted(diffs, key=lambda x: x[2]):
        lines.append(f"- Δ{d:+d} `{c}` {q}")
    lines.append("")
    lines.append("## 判读（Owner 裁决③：总分不掉运行间抖动带 ±0.2 + 红线满维 → 切默认）\n")
    hold = (ob - oa) >= -0.2 and red_b == 2 * red_n
    lines.append(f"- 总分差 {ob - oa:+.2f}（≥ -0.2 即守线：{'✅' if ob - oa >= -0.2 else '❌'}）")
    lines.append(f"- 红线 compliance 满维：{'✅' if red_b == 2 * red_n else '❌'}（{red_b}/{2 * red_n}）")
    lines.append(
        f"- **结论：{'守线成立且显著加分，按裁决③切 pipeline 为默认引擎（单循环留旗标对照）' if hold else '守线不成立，双轨再观察一周'}**\n"
    )

    detail = []
    for case, sa, sb in zip(golden, arm_a, arm_b):
        detail.append(
            {"category": case["category"], "query": case["query"], "inline": sa, "pipeline": sb}
        )
    with open(out_path.replace(".md", "-detail.json"), "w", encoding="utf-8") as f:
        json.dump(detail, f, ensure_ascii=False, indent=2)

    open(out_path, "w", encoding="utf-8").write("\n".join(lines))
    print("\n".join(lines))
    print(f"\n报告: {out_path}")


if __name__ == "__main__":
    main()
