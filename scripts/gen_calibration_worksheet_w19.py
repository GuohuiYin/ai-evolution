#!/usr/bin/env python3
"""W19 Judge 校准复测 · 双盲工作表生成器（W18 同款流程，抽测 10 案）

与 W18 的差异：① 22 案全量 → 10 案分层抽样（低 3/中 4/高 3，覆盖新案、翻案、
回归候选、挂账加重、redline 代表）；② 回答解析适配 v3 日志结构（被测回答取
SSE dump 中 AssistantMessage 的 "text" 字段，JSON 转义单行）。

双盲红线：judge 分数与理由一律不进入工作表。
用法：python3 scripts/gen_calibration_worksheet_w19.py
产出：docs/eval/generation/w19-judge-calibration-worksheet-owner.md
"""

import json
import random
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LOG = ROOT / "docs/eval/generation/logs/w19-gen-v3-rerun2.log"
GOLDEN = ROOT / "src/main/resources/eval/golden-set-generation.json"
OUT_DIR = ROOT / "docs/eval/generation"

SEED_ORDER = 20261020
SEED_AB = 20261021

# 分层抽样清单（抽样的 R1 水位只用于分层，分数不入表）：
# 低分段 3：法定代表人(挂账加重)、中期分红算(回归候选)、G1(新案失败)
# 中分段 4：冰淇淋占比(历史抖动)、2024净利分红(回归候选)、固态电池(修正案)、600519营收+工艺(口径代表)
# 高分段 3：G2(新案)、储能收入翻案(翻案)、一季度(修正案)
SAMPLE = [
    "茅台的法定代表人是谁",
    "中期分红算在里面吗",
    "宁德时代 2025 年营业总收入是多少",
    "茅台冰淇淋业务的营收占比是多少",
    "茅台 2024 年净利润和分红情况如何",
    "宁德时代固态电池什么时候量产",
    "600519 2024 年营收多少？顺便查年报里的工艺描述",
    "茅台 2025 年归母净利润是多少",
    "宁德时代 2024 年储能业务收入是多少亿元",
    "茅台 2025 年一季度的营收是多少",
]

USER_TEXT_RE = re.compile(r'^\s*"text" : "(?P<text>.+)",\s*$')
ASSIST_TEXT_RE = re.compile(r'^\s*"text" : "(?P<text>.+)",?\s*$')


def unescape(s: str) -> str:
    return s.replace("\\n", "\n").replace('\\"', '"').replace("\\\\", "\\").rstrip(",")


def parse_answers(log_path, queries):
    """对每条抽样 query：取其最后一次以 UserMessage 出现的位置，向后找第一条
    长度足够且非 prompt 回显的 "text" 行作为被测回答（AssistantMessage JSON 单行）。"""
    lines = log_path.read_text(encoding="utf-8").splitlines()
    results = {}
    for q in queries:
        answer = None
        thought_cand = None
        starts = [i for i, ln in enumerate(lines)
                  if "stage=ROUTE" in ln and ln.rstrip().endswith("msg=" + q)]
        for start in starts:
            # ReAct 循环内同案有多条 assistant text（Thought/Action 中间步在前，
            # 最终答案在后）——取区域内最后一条合格 text（judge 评的就是最终答案）
            region_end = next((k for k in range(start + 1, len(lines))
                               if "stage=ROUTE" in lines[k]), min(start + 2000, len(lines)))
            for j in range(start + 1, region_end):
                m = ASSIST_TEXT_RE.match(lines[j])
                if not m:
                    continue
                cand = unescape(m.group("text"))
                if (cand == q or len(cand) < 30 or "你是一位严谨" in cand
                        or (cand.lstrip().startswith("{") and "accuracy" in cand)):
                    continue
                if cand.lstrip().startswith("Thought:"):
                    thought_cand = cand  # 最终答案疑为 Thought 泄漏——缺陷现场，兜底保留
                    continue
                answer = cand  # 不 break，持续覆盖至区域末，留下最终答案
            if not answer and thought_cand:
                answer = thought_cand  # 两趟策略：干净答案优先，泄漏现场兜底
        if not answer:
            raise AssertionError(f"未解析到回答：{q}")
        results[q] = answer
    return results


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
    by_query = {}
    for c in golden:
        by_query.setdefault(c["query"], c)  # 跨类别重复 query 取首条（抽样清单内无重复）
    answers = parse_answers(LOG, SAMPLE)
    assert len(answers) == 10

    order_rng = random.Random(SEED_ORDER)
    ab_rng = random.Random(SEED_AB)
    indices = list(range(len(SAMPLE)))
    order_rng.shuffle(indices)

    blocks = []
    for no, idx in enumerate(indices, 1):
        q = SAMPLE[idx]
        blocks.append(case_block(no, by_query[q], answers[q], ab_rng.random() < 0.5))

    header = ("# W19 Judge 校准复测 · 打分工作表（Owner 专用）\n\n"
              f"> 生成：gen_calibration_worksheet_w19.py，种子 {SEED_ORDER}/{SEED_AB}，"
              "分层抽测 10 案（新语料时效性复测，W19 #3 守线 ≥80%）。\n"
              "> 本表不含 judge 分数；打分期间禁止查看跑批日志（双盲纪律）。\n\n"
              + RUBRIC)
    out = OUT_DIR / "w19-judge-calibration-worksheet-owner.md"
    out.write_text(header + "\n".join(blocks) + "\n", encoding="utf-8")
    print(f"written: {out.name}")


if __name__ == "__main__":
    main()
