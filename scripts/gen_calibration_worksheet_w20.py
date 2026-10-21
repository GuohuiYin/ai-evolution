#!/usr/bin/env python3
"""W20 Judge 校准重生 · 双盲工作表生成器（W19 同分层同种子口径，守线 ≥80%）

与 W19 的关键差异——提取假象源消除（W20 #2①）：
回答不再从 SSE/Advisor 日志区域扫描「最后一条 text」（MAX_STEPS 触顶时会捞到
ReAct 中间步，把评测假象当泄漏——W19 F3 案根因），改为直接取
GenerationEvalRunner 报告段的「回答：」块——即 chatService.chat() 的真实返回
（LoopResult 终答 / 合成步产物 / 兜底文案，含免责声明尾），judge 评的就是它，
用户拿到的也是它，三方同源。

双盲红线：judge 分数与理由一律不进入工作表。
用法：python3 scripts/gen_calibration_worksheet_w20.py
产出：docs/eval/generation/w20-judge-calibration-worksheet-owner.md
"""

import json
import random
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LOG = ROOT / "docs/eval/generation/logs/w20-synthesis-v1-gen.log"
GOLDEN = ROOT / "src/main/resources/eval/golden-set-generation.json"
OUT_DIR = ROOT / "docs/eval/generation"

# 与 W19 完全同口径：分层清单、顺序种子、甲乙种子（可复现 = 可对照）
SEED_ORDER = 20261020
SEED_AB = 20261021

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

JUDGE_RE = re.compile(
    r"GenerationEvalRunner : \[(?P<cat>[\w-]+)\]\[(?P<query>.+?)\] 得分=")
ANSWER_START_RE = re.compile(r"^\s*回答：(?P<first>.*)$")
LOG_LINE_RE = re.compile(r"^\d{2}:\d{2}:\d{2}\.\d{3}\s")


def parse_answers_from_report(log_path, queries):
    """报告段提取：每条 judge 明细行后跟「  回答：」块（可多行），
    至下一条时间戳日志行止。query → answer（重复 query 取首条，同 W19 口径）。"""
    lines = log_path.read_text(encoding="utf-8").splitlines()
    by_query = {}
    i = 0
    while i < len(lines):
        if not JUDGE_RE.search(lines[i]):
            i += 1
            continue
        query = JUDGE_RE.search(lines[i]).group("query")
        # 从 judge 行内（理由= 可能换行）向后找「回答：」起始
        j = i + 1
        block = None
        while j < len(lines) and not LOG_LINE_RE.match(lines[j]):
            m = ANSWER_START_RE.match(lines[j])
            if m:
                buf = [m.group("first")]
                k = j + 1
                while k < len(lines) and not LOG_LINE_RE.match(lines[k]):
                    buf.append(lines[k])
                    k += 1
                block = "\n".join(buf).strip()
                break
            j += 1
        if block and query not in by_query:
            by_query[query] = block
        i += 1
    results = {}
    for q in queries:
        if q not in by_query:
            raise AssertionError(f"报告段未找到回答：{q}")
        results[q] = by_query[q]
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
    answers = parse_answers_from_report(LOG, SAMPLE)
    assert len(answers) == 10

    order_rng = random.Random(SEED_ORDER)
    ab_rng = random.Random(SEED_AB)
    indices = list(range(len(SAMPLE)))
    order_rng.shuffle(indices)

    blocks = []
    for no, idx in enumerate(indices, 1):
        q = SAMPLE[idx]
        blocks.append(case_block(no, by_query[q], answers[q], ab_rng.random() < 0.5))

    header = ("# W20 Judge 校准重生 · 打分工作表（Owner 专用）\n\n"
              f"> 生成：gen_calibration_worksheet_w20.py，种子 {SEED_ORDER}/{SEED_AB}（W19 同分层同种子），"
              "抽测 10 案，跑批=w20-synthesis-v1-gen.log（泄漏修复后栈）。\n"
              "> 提取口径：eval 报告段「回答：」块=chat() 真实返回（MAX_STEPS 假象源已消除，W20 #2①）。\n"
              "> 本表不含 judge 分数；打分期间禁止查看跑批日志与 eval 报告（双盲纪律）。\n\n"
              + RUBRIC)
    out = OUT_DIR / "w20-judge-calibration-worksheet-owner.md"
    out.write_text(header + "\n".join(blocks) + "\n", encoding="utf-8")
    print(f"written: {out.name}")


if __name__ == "__main__":
    main()
