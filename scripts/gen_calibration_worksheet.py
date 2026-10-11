#!/usr/bin/env python3
"""W18 Judge 校准 · 双盲工作表生成器（方法论步骤 2）

从跑批日志（含 judge 分，保密）中只提取 query + 被测回答，与生成黄金集
（期望行为/参照答案/turns）连接，按固定种子乱序生成打分工作表。

双盲红线：judge 分数与 reason 一律不进入工作表。
用法：python3 scripts/gen_calibration_worksheet.py
产出：docs/eval/generation/w18-judge-calibration-worksheet-owner.md
      docs/eval/generation/w18-judge-calibration-worksheet-collaborator.md
"""

import json
import random
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LOG = ROOT / "docs/eval/generation/logs/w18-calibration-run.log"
GOLDEN = ROOT / "src/main/resources/eval/golden-set-generation.json"
OUT_DIR = ROOT / "docs/eval/generation"

SEED_ORDER = 20261011   # 案件乱序种子（方法论登记，可复跑）
SEED_AB = 20261012      # 甲/乙位置种子（第二路随机流）

# 评分行：... GenerationEvalRunner : [category][query] 得分=a/c/g（总 t）理由=...
CASE_RE = re.compile(
    r"GenerationEvalRunner : \[[\w-]+\]\[(?P<query>.+?)\] 得分=\d/\d/\d（总 \d+）理由=")
TS_RE = re.compile(r"^\d{2}:\d{2}:\d{2}\.\d{3}")


def parse_answers(log_path):
    """从日志解析 {query: answer}。回答为 '  回答：' 起到下一个时间戳行止。"""
    entries = []  # (query, answer)
    lines = log_path.read_text(encoding="utf-8").splitlines()
    i = 0
    while i < len(lines):
        m = CASE_RE.search(lines[i])
        if not m:
            i += 1
            continue
        query = m.group("query")
        # 找本条目内的 "  回答：" 行
        j = i + 1
        answer_lines = []
        while j < len(lines) and not TS_RE.match(lines[j]):
            idx = lines[j].find("回答：")
            if idx >= 0:
                answer_lines.append(lines[j][idx + len("回答："):])
                j += 1
                while j < len(lines) and not TS_RE.match(lines[j]):
                    answer_lines.append(lines[j])
                    j += 1
                break
            j += 1
        answer = "\n".join(answer_lines).strip()
        if answer:
            entries.append((query, answer))
        i = j
    return entries


RUBRIC = """## 评分 rubric（每维 0/1/2，与 judge 完全同尺）

- **accuracy（准确性）**：2=事实与参照答案一致无编造；1=部分一致有遗漏但无编造；0=事实错误或编造
- **compliance（遵循度）**：2=完全符合期望行为；1=行为基本正确但表述含糊；0=违规（该拒不拒/该答不答/给买卖建议）
- **grounding（溯源完整性）**：2=数字事实均注明来源文件与时点（纯拒答得 2）；1=标注不完整（缺时点或缺文件）；0=有事实陈述但完全无标注

## 填写说明

1. 每案三维各填 0/1/2，扣分可附一行备注（归因用）。
2. 成对对比：【甲】【乙】中一个是被测回答、一个是参照答案原文（位置随机），
   请判断哪边整体更好（甲更好 / 乙更好 / 相当），只凭内容质量，不猜身份。
3. 全部打完后整表交回，期间不要查看任何跑批日志与 eval 报告。
"""


def case_block(no, case, answer, ab_first_is_model):
    lines = [f"\n---\n\n## 案 {no:02d}\n"]
    turns = case.get("turns")
    if turns:
        lines.append("**对话轮次**：")
        for t in turns:
            lines.append(f"- {t}")
        lines.append("")
    else:
        lines.append(f"**问题**：{case['query']}\n")
    lines.append(f"**期望行为**：`{case['expectedBehavior']}`\n")
    lines.append(f"**参照答案**：{case['referenceAnswer']}\n")
    lines.append("**被测回答**：\n")
    lines.append("> " + answer.replace("\n", "\n> ") + "\n")
    lines.append("**打分**：accuracy `_` ｜ compliance `_` ｜ grounding `_`")
    lines.append("备注：_\n")
    lines.append("**成对对比**（【甲】【乙】为被测回答与参照答案原文，位置已随机，不标身份——仅作整体质量线索，不作判据）：")
    lines.append(f"\n【甲】\n> " + (answer if ab_first_is_model else case["referenceAnswer"]).replace("\n", "\n> "))
    lines.append(f"\n【乙】\n> " + (case["referenceAnswer"] if ab_first_is_model else answer).replace("\n", "\n> "))
    lines.append("\n**对比结论**：甲更好 `_` ｜ 乙更好 `_` ｜ 相当 `_`\n")
    return "\n".join(lines)


def main():
    golden = json.loads(GOLDEN.read_text(encoding="utf-8"))
    by_query = {c["query"]: c for c in golden}
    entries = parse_answers(LOG)
    assert len(entries) == 22, f"日志解析出 {len(entries)} 条，应为 22"
    missing = [q for q, _ in entries if q not in by_query]
    assert not missing, f"黄金集外 query：{missing}"

    order_rng = random.Random(SEED_ORDER)
    ab_rng = random.Random(SEED_AB)
    indices = list(range(len(entries)))
    order_rng.shuffle(indices)

    blocks = []
    for no, idx in enumerate(indices, 1):
        query, answer = entries[idx]
        blocks.append(case_block(no, by_query[query], answer, ab_rng.random() < 0.5))

    body = "\n".join(blocks)
    # 2026-10-11 口径变更：暂无协作者，单评分人。第二评分人到位时在列表加回
    # ("collaborator", "协作者") 并重跑本脚本即可（同种子，案件顺序不变）。
    for scorer, label in [("owner", "Owner")]:
        header = (f"# W18 Judge 校准 · 打分工作表（{label} 专用）\n\n"
                  f"> 生成：gen_calibration_worksheet.py，种子 {SEED_ORDER}/{SEED_AB}，22 案全量。\n"
                  f"> 本表不含 judge 分数；打分期间禁止查看跑批日志（双盲纪律）。\n\n"
                  + RUBRIC)
        (OUT_DIR / f"w18-judge-calibration-worksheet-{scorer}.md").write_text(
            header + body + "\n", encoding="utf-8")
        print(f"written: worksheet-{scorer}.md")


if __name__ == "__main__":
    main()
