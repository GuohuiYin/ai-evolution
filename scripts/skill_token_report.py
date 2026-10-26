#!/usr/bin/env python3
"""W22 #2：Skill 渐进披露 Token 降幅实测报告（设计 §2.4 口径）。

口径：
- 逐案生成侧 prompt token 真账 = 该案 stage=ROUTE 到下一案 ROUTE 之间所有
  stage=MODEL 日志的 tokens_in 之和（TokenUsageAdvisor 只挂 ChatClient，
  judge 走 ChatModel 天然不入账）；
- 降幅守线：B 臂平均 prompt token 降幅 ≥40% 判值得，<20% 登记止步；
- 质量守线：judge 总分跌落须在运行间抖动带（±0.2）内；
- 误判防线：逐案触发记录（skill triggered 日志）随报告输出。

用法：python3 scripts/skill_token_report.py <full臂.log> <progressive臂.log> <输出.md>
"""

import re
import statistics
import sys

GOLDEN_SET = "src/main/resources/eval/golden-set-generation.json"
ROUTE = re.compile(r"stage=ROUTE")
MODEL = re.compile(r"stage=MODEL .* tokens_in=(\d+)")
TRIGGER = re.compile(r"skill triggered: \[([^\]]*)\]")


def segments(path):
    """按 ROUTE 边界切成"轮"段：返回 (每轮 token 列表, 每轮触发技能列表)。

    注意一轮 ≠ 一案：多轮案按 turns 顺序重放（GenerationEvaluator），每轮一条 ROUTE。
    """
    seg_tokens, seg_skills = [], []
    cur_tokens, cur_skills, in_seg = 0, [], False
    for line in open(path, encoding="utf-8", errors="replace"):
        if ROUTE.search(line):
            if in_seg:
                seg_tokens.append(cur_tokens)
                seg_skills.append(cur_skills)
            cur_tokens, cur_skills, in_seg = 0, [], True
            continue
        m = MODEL.search(line)
        if m and in_seg:
            cur_tokens += int(m.group(1))
        t = TRIGGER.search(line)
        if t and in_seg:
            cur_skills.extend(s for s in t.group(1).split(", ") if s)
    if in_seg:
        seg_tokens.append(cur_tokens)
        seg_skills.append(cur_skills)
    return seg_tokens, seg_skills


def align_cases(seg_tokens, seg_skills):
    """按黄金集顺序把轮段聚合回案（多轮案 = len(turns) 段求和）。"""
    import json

    golden = json.load(open(GOLDEN_SET, encoding="utf-8"))
    cases_tokens, cases_skills, i = [], [], 0
    for case in golden:
        turns = len(case.get("turns", [])) or 1
        if i + turns > len(seg_tokens):
            break  # 日志截断：剩余案不计
        cases_tokens.append(sum(seg_tokens[i : i + turns]))
        cases_skills.append([s for sk in seg_skills[i : i + turns] for s in sk])
        i += turns
    return cases_tokens, cases_skills


def pct(values, q):
    if not values:
        return 0.0
    s = sorted(values)
    return s[min(len(s) - 1, int(len(s) * q))]


def main():
    full_tokens, _ = align_cases(*segments(sys.argv[1]))
    prog_tokens, prog_skills = align_cases(*segments(sys.argv[2]))
    out_path = sys.argv[3]

    n = min(len(full_tokens), len(prog_tokens))
    full_tokens, prog_tokens, prog_skills = full_tokens[:n], prog_tokens[:n], prog_skills[:n]

    mean_f = statistics.mean(full_tokens)
    mean_p = statistics.mean(prog_tokens)
    drop = (mean_f - mean_p) / mean_f if mean_f else 0.0

    triggered_idx = [i for i, sk in enumerate(prog_skills) if sk]
    untriggered_idx = [i for i, sk in enumerate(prog_skills) if not sk]

    def group_mean(tokens, idx):
        return statistics.mean(tokens[i] for i in idx) if idx else 0.0

    lines = []
    lines.append("# W22 #2 实测：Skill 渐进披露 Token 降幅（full vs progressive）\n")
    lines.append("> 2026-10-24。57 案 × 2 臂，生成侧 prompt token 真账（usage.prompt_tokens 逐案聚合，")
    lines.append("> judge 走 ChatModel 不经 TokenUsageAdvisor，天然不入账）。\n")
    lines.append(f"- 配对案数：{n}（full {len(full_tokens)} / progressive {len(prog_tokens)}）\n")
    lines.append("## Token 真账（生成侧 prompt tokens/案）\n")
    lines.append("| 指标 | A 臂 full 全量常驻 | B 臂 progressive 按需 | 降幅 |")
    lines.append("|---|---|---|---|")
    lines.append(f"| 平均 | {mean_f:.0f} | {mean_p:.0f} | **{drop:.1%}** |")
    lines.append(f"| P50 | {pct(full_tokens, 0.5):.0f} | {pct(prog_tokens, 0.5):.0f} | — |")
    lines.append(f"| P95 | {pct(full_tokens, 0.95):.0f} | {pct(prog_tokens, 0.95):.0f} | — |")
    lines.append(f"| 单批合计（{n} 案） | {sum(full_tokens)} | {sum(prog_tokens)} | {sum(full_tokens) - sum(prog_tokens)} |")
    lines.append("")
    lines.append("## 按触发分组（B 臂）\n")
    lines.append(f"- 触发技能案：{len(triggered_idx)} 案，平均 {group_mean(prog_tokens, triggered_idx):.0f} tokens（对照 A 臂同案 {group_mean(full_tokens, triggered_idx):.0f}）")
    lines.append(f"- 未触发案：{len(untriggered_idx)} 案，平均 {group_mean(prog_tokens, untriggered_idx):.0f} tokens（对照 A 臂同案 {group_mean(full_tokens, untriggered_idx):.0f}）")
    lines.append(f"- 逐案触发：{[i + 1 for i in triggered_idx]}（案号，1 起）\n")
    lines.append("## 判读（Owner 守线：降幅 ≥40% 值得 / <20% 止步；质量跌落 ≤ 抖动带 ±0.2）\n")
    verdict = "≥40%，机制判「值得」" if drop >= 0.4 else ("<20%，登记认知止步" if drop < 0.2 else "20–40% 区间，按质量分与扩列前景综合判")
    lines.append(f"- Token 降幅 **{drop:.1%}** → {verdict}")
    lines.append("- 质量对照见同批 judge 得分（双臂报告另行生成）\n")

    open(out_path, "w", encoding="utf-8").write("\n".join(lines))
    print("\n".join(lines))
    print(f"\n报告: {out_path}")


if __name__ == "__main__":
    main()
