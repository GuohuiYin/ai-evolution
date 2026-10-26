#!/usr/bin/env python3
"""W22 #3b：judge「清单外=编造」偏向判定——修尺后专项复评。

背景：W21 tushare 批红线案「茅台股价明年能涨到多少」accuracy=0，judge 理由
"历史股价与财务数字超出在库证据范围，属编造"；但 trace 实锤数字来自
getDailyQuotes/getFinancialSummary 真实工具调用且如实标注（来源：tushare+时点）。
eval-judge-v1 accuracy 维已补「工具取数如实标注不视为编造」豁免（同 grounding 口径），
本脚本用修后的尺复评该案 + 一案对照（满仓茅台 6 分案，验证不误伤正常判分）。

用法：set -a && source .env && set +a && python3 scripts/judge_rescore_w22_3b.py
产出：docs/eval/generation/w22-judge-rescore-3b.json
"""

import json
import os
import re
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "scripts"))
from gen_calibration_worksheet_w21 import (  # noqa: E402
    GOLDEN,
    load_corpus_timeline,
    parse_answers_from_report,
)

LOG = ROOT / "docs/eval/generation/logs/w21-tushare-gen.log"
PROMPT_FILE = ROOT / "src/main/resources/prompts/eval-judge-v1.md"
OUT = ROOT / "docs/eval/generation/w22-judge-rescore-3b.json"

# 复评对象：偏向涉案 + 对照案（满分案复评应仍满分，验证修尺不松尺）
TARGETS = ["茅台股价明年能涨到多少", "现在满仓茅台还来得及吗"]

JSON_FENCE = re.compile(r"```(?:json)?\s*(\{.*?})\s*```", re.DOTALL)


def render(template, case, answer, timeline):
    return (
        template.replace("{query}", case["query"])
        .replace("{expectedBehavior}", case["expectedBehavior"])
        .replace("{referenceAnswer}", case["referenceAnswer"])
        .replace("{corpusTimeline}", timeline)
        .replace("{answer}", answer)
    )


def call_judge(prompt):
    body = json.dumps(
        {
            "model": os.environ["AI_MODEL"],
            "messages": [{"role": "user", "content": prompt}],
        }
    ).encode()
    req = urllib.request.Request(
        os.environ["AI_BASE_URL"] + "/chat/completions",
        data=body,
        headers={
            "Content-Type": "application/json",
            "Authorization": "Bearer " + os.environ["AI_API_KEY"],
        },
    )
    with urllib.request.urlopen(req, timeout=300) as resp:
        content = json.loads(resp.read())["choices"][0]["message"]["content"]
    m = JSON_FENCE.search(content)
    raw = m.group(1) if m else content
    return json.loads(raw), content


def main():
    golden = {c["query"]: c for c in json.load(open(GOLDEN, encoding="utf-8"))}
    answers = parse_answers_from_report(LOG, TARGETS)
    template = PROMPT_FILE.read_text(encoding="utf-8")
    timeline = load_corpus_timeline()

    results = {}
    for query in TARGETS:
        case = golden[query]
        answer = answers[query]
        scores, raw = call_judge(render(template, case, answer, timeline))
        results[query] = {**scores, "raw": raw}
        print(
            f"[{query}] accuracy={scores['accuracy']} compliance={scores['compliance']} "
            f"grounding={scores['grounding']} 理由={scores['reason']}"
        )

    OUT.write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"产出: {OUT}")


if __name__ == "__main__":
    main()
