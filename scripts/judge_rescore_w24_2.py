#!/usr/bin/env python3
"""W24 #2：judge 拒答口径对齐（检索信号注入）——定向复评。

背景：W23 基线涉案案「其中的 12987 是什么含义」真实硬拒答（召回为空），旧尺
compliance=0「无理由拒答」；eval-judge-v1 新增检索信号段（empty-retrieval 不算
无理由拒答，accuracy 照判）。本脚本复评：
1. 涉案案（W23 基线回答 + empty-retrieval 信号）——compliance 应翻分、accuracy 不动；
2. 两案满分对照（W24 重试批回答 + none 信号）——应仍满分，验证信号不误伤正常判分。

用法：set -a && source .env && set +a && python3 scripts/judge_rescore_w24_2.py
产出：docs/eval/generation/w24-judge-rescore-2.json
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

PROMPT_FILE = ROOT / "src/main/resources/prompts/eval-judge-v1.md"
OUT = ROOT / "docs/eval/generation/w24-judge-rescore-2.json"

# (query, 取回答的日志, 检索信号)
TARGETS = [
    ("其中的 12987 是什么含义", "w23-ragv3-gen-eval.log", "empty-retrieval（本次拒答为召回为空的设计性行为）"),
    ("12987 每个数字代表什么", "w24-retry-gen-eval.log", "none"),
    ("茅台的法定代表人是谁", "w24-retry-gen-eval.log", "none"),
]

JSON_FENCE = re.compile(r"```(?:json)?\s*(\{.*?})\s*```", re.DOTALL)


def render(template, case, answer, timeline, signal):
    return (
        template.replace("{query}", case["query"])
        .replace("{expectedBehavior}", case["expectedBehavior"])
        .replace("{referenceAnswer}", case["referenceAnswer"])
        .replace("{corpusTimeline}", timeline)
        .replace("{retrievalSignal}", signal)
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
    template = PROMPT_FILE.read_text(encoding="utf-8")
    timeline = load_corpus_timeline()

    results = {}
    for query, log_name, signal in TARGETS:
        log = ROOT / "docs/eval/generation/logs" / log_name
        answer = parse_answers_from_report(log, [query])[query]
        case = golden[query]
        scores, raw = call_judge(render(template, case, answer, timeline, signal))
        results[query] = {"signal": signal, **scores, "raw": raw}
        print(
            f"[{query}] signal={signal[:15]} accuracy={scores['accuracy']} "
            f"compliance={scores['compliance']} grounding={scores['grounding']} 理由={scores['reason']}"
        )

    OUT.write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"产出: {OUT}")


if __name__ == "__main__":
    main()
