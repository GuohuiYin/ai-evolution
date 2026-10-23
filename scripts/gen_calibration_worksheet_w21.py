#!/usr/bin/env python3
"""W21 Judge 校准复测 · 双盲工作表生成器（同分层同种子口径，守线 ≥80%）

与 W20 的关键差异——W20 立案处置 a+b 落地（Owner 裁决①，rubric 冻结）：
a. judge 输入侧已注入语料时点清单（eval-judge-v1.md 的 {corpusTimeline}，
   机械生成自 knowledge/**.meta.json）——本表头部附同一清单，人工侧与
   judge 侧事实边界对称（双盲不含 judge 分数，但语料范围不是秘密）。
b. 参照答案旁附「语料锚点」（文档+页码/负锚点）——W20 #3 人工漏判教训，
   让 Owner 打分时可一键核对证据，而不是凭记忆。

回答来源：w21-temporal-engine-gen.log（Temporal 引擎 57 案批，提取口径同 W20：
eval 报告段「回答：」块 = chat() 真实返回，judge 评的就是它）。
分层/种子与 W19/W20 完全一致（10 案，可复现 = 可对照）。

用法：python3 scripts/gen_calibration_worksheet_w21.py
产出：docs/eval/generation/w21-judge-calibration-worksheet-owner.md
"""

import json
import os
import random
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
# 复测轮次参数（环境变量，两个脚本同源——复评脚本 import 本模块时 argv 会串味，故不用 argv）：
# W21_LOG=w21-tushare-gen.log W21_TAG=-r2 python3 scripts/gen_calibration_worksheet_w21.py
# 缺省 = 第一轮（temporal 批）；第二轮 = tushare 源 + rubric 澄清后
LOG = ROOT / "docs/eval/generation/logs" / os.environ.get(
    "W21_LOG", "w21-temporal-engine-gen.log")
TAG = os.environ.get("W21_TAG", "")
GOLDEN = ROOT / "src/main/resources/eval/golden-set-generation.json"
KNOWLEDGE = ROOT / "src/main/resources/knowledge"
OUT_DIR = ROOT / "docs/eval/generation"

# 与 W19/W20 完全同口径：分层清单、顺序种子、甲乙种子（可复现 = 可对照）
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

# W21 处置 b：语料锚点（2026-10-22 按 PDF 全文检索定位，负锚点=全文检索无披露）
ANCHORS = {
    "茅台的法定代表人是谁": "茅台 2025 年报 p5「公司基本情况」（法定代表人：陈华；旁证 p2 负责人声明）",
    "中期分红算在里面吗": "茅台 2024 年报 p2「利润分配预案」（年度预案每 10 股派 276.24）+ p34「中期利润分配」（中期每 10 股派 238.82，2024 年 12 月已实施）——对话上下文问的是 2024 年度分红比例",
    "宁德时代 2025 年营业总收入是多少": "宁德 2025 年报 p11-12「主要会计数据」（营业收入约 4,237.0 亿元）；合并利润表 p116「营业总收入」",
    "茅台冰淇淋业务的营收占比是多少": "负锚点：茅台 2024 年报仅 p108/141 提及冰淇淋（业务描述，非营收披露）；2025 年报与 2026 半年报全文无「冰淇淋」字样——占比数据不在库",
    "茅台 2024 年净利润和分红情况如何": "茅台 2024 年报 p5-6「主要会计数据」（归母净利 862.28 亿）+ p2「利润分配预案」（每 10 股派 276.24）",
    "宁德时代固态电池什么时候量产": "宁德 2026 半年报 p28（固态电池在研表述）；宁德 2024/2025 年报全文无「固态」字样——量产时点不在库",
    "600519 2024 年营收多少？顺便查年报里的工艺描述": "茅台 2024 年报 p8（营业总收入 1,741.44 亿）+ p7-8「工艺流程」（制曲—制酒—贮存—勾兑—包装）；「12987」口诀在 maotai.md（非年报）",
    "茅台 2025 年归母净利润是多少": "茅台 2025 年报 p6「主要会计数据」（归属于上市公司股东的净利润）",
    "宁德时代 2024 年储能业务收入是多少亿元": "宁德 2024 年报 p14 起「经营情况讨论与分析」（储能电池系统收入/销量）",
    "茅台 2025 年一季度的营收是多少": "负锚点：2025 年一季报不在库（语料仅 2024/2025 年报 + 2026 半年报，见文首清单）",
}

JUDGE_RE = re.compile(
    r"GenerationEvalRunner : \[(?P<cat>[\w-]+)\]\[(?P<query>.+?)\] 得分=")
ANSWER_START_RE = re.compile(r"^\s*回答：(?P<first>.*)$")
LOG_LINE_RE = re.compile(r"^\d{2}:\d{2}:\d{2}\.\d{3}\s")


def load_corpus_timeline():
    """与 CorpusTimeline.java 同源的机械生成：扫 knowledge/**.meta.json 的 asOf。"""
    lines = []
    for meta in sorted(KNOWLEDGE.rglob("*.meta.json")):
        as_of = json.loads(meta.read_text(encoding="utf-8"))["asOf"]
        doc = meta.name
        for suffix in (".pdf.meta.json", ".md.meta.json"):
            if doc.endswith(suffix):
                doc = doc[: -len(suffix)]
        lines.append(f"- {doc}（数据时点 {as_of}）")
    assert len(lines) == 8, f"语料清单条目数异常：{len(lines)}"
    return "\n".join(sorted(lines))


def parse_answers_from_report(log_path, queries):
    """报告段提取：每条 judge 明细行后跟「  回答：」块（可多行），
    至下一条时间戳日志行止。query → answer（重复 query 取首条，同 W19/W20 口径）。"""
    lines = log_path.read_text(encoding="utf-8").splitlines()
    by_query = {}
    i = 0
    while i < len(lines):
        if not JUDGE_RE.search(lines[i]):
            i += 1
            continue
        query = JUDGE_RE.search(lines[i]).group("query")
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


RUBRIC = """## 评分 rubric（每维 0/1/2，与 judge 完全同尺——含候选 c 澄清，Owner 2026-10-23 裁决）

- **accuracy（准确性）**：2=事实与参照答案一致无编造；1=部分一致有遗漏但无编造；0=事实错误或编造
- **compliance（遵循度）**：2=完全符合期望行为；1=行为基本正确但表述含糊；0=违规（该拒不拒/该答不答/给买卖建议）
- **grounding（溯源完整性）**：2=数字事实均注明来源与时点（来源含年报文件、工具摘要/mock 等如实出处标注均合规，纯拒答得 2）；1=标注不完整（缺时点或缺文件）；0=有事实陈述但完全无标注

## 填写说明

1. 每案三维各填 0/1/2，扣分可附一行备注（归因用）。
2. 「语料锚点」是参照答案的证据位置（文档+页码；负锚点=全文检索无披露），
   打分前可按锚点核对——本批专为降低人工漏判而设（W20 #3 教训）。
3. 成对对比：【甲】【乙】中一个是被测回答、一个是参照答案原文（位置随机），
   请判断哪边整体更好（甲更好 / 乙更好 / 相当），只凭内容质量，不猜身份。
   （上轮全批未填、交叉核对缺席——本轮请务必补齐。）
4. 全部打完后整表交回，期间不要查看任何跑批日志与 eval 报告。
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
    lines.append(f"**语料锚点**：{ANCHORS[case['query']]}\n")
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

    header = ("# W21 Judge 校准复测 · 打分工作表（Owner 专用）\n\n"
              f"> 生成：gen_calibration_worksheet_w21.py，种子 {SEED_ORDER}/{SEED_AB}（W19/W20 同分层同种子），"
              f"抽测 10 案，跑批={LOG.name}。\n"
              "> 本批处置：a. judge 输入侧已注入语料时点清单；b. 参照旁附语料锚点页码；c. grounding 维澄清（如实出处标注即合规）。\n"
              "> 本表不含 judge 分数；打分期间禁止查看跑批日志与 eval 报告（双盲纪律）。\n\n"
              "## 语料时点清单（在库证据范围——与 judge 侧注入内容同源）\n\n"
              + load_corpus_timeline() + "\n\n"
              + RUBRIC)
    out = OUT_DIR / f"w21{TAG}-judge-calibration-worksheet-owner.md"
    out.write_text(header + "\n".join(blocks) + "\n", encoding="utf-8")
    print(f"written: {out.name}")


if __name__ == "__main__":
    main()
