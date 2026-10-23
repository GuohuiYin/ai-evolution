#!/usr/bin/env python3
"""W21 Judge 校准复测 · judge 复评器（处置 a 生效后的新 prompt 重评）

对 w21-temporal-engine-gen.log 中抽样 10 案的真实回答，用注入语料时点清单后的
eval-judge-v1 重新打分。复用 gen_calibration_worksheet_w21 的提取与清单生成逻辑，
保证与工作表评的是同一批回答（校准有效性的前提）。

双盲纪律：输出 json 含 judge 分数，Owner 打分完成前不展示、不入工作表。
用法：set -a && source .env && set +a && python3 scripts/judge_calibration_score_w21.py
产出：docs/eval/generation/w21-judge-rescore.json
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
    LOG,
    SAMPLE,
    load_corpus_timeline,
    parse_answers_from_report,
)

PROMPT_FILE = ROOT / "src/main/resources/prompts/eval-judge-v1.md"
# 复测轮次参数（环境变量 W21_TAG/W21_LOG，与工作表生成器同源）：
# W21_LOG=w21-tushare-gen.log W21_TAG=-r2 python3 scripts/judge_calibration_score_w21.py
TAG = os.environ.get("W21_TAG", "")
OUT = ROOT / f"docs/eval/generation/w21{TAG}-judge-rescore.json"

JSON_FENCE = re.compile(r"```(?:json)?\s*(\{.*?})\s*```", re.DOTALL)


def render(template, case, answer, timeline):
    """与 Java PromptTemplate 同语义：按占位符名单纯替换（模板内 \\{ 转义不受影响）。"""
    return (
        template.replace("{query}", case["query"])
        .replace("{expectedBehavior}", case["expectedBehavior"])
        .replace("{referenceAnswer}", case["referenceAnswer"])
        .replace("{corpusTimeline}", timeline)
        .replace("{answer}", answer)
    )


def call_judge(prompt):
    base = os.environ.get("AI_BASE_URL", "https://api.deepseek.com")
    body = json.dumps(
        {
            "model": os.environ.get("AI_MODEL", "deepseek-v4-flash"),
            "messages": [{"role": "user", "content": prompt}],
        }
    ).encode("utf-8")
    req = urllib.request.Request(
        base + "/chat/completions",
        data=body,
        headers={
            "Content-Type": "application/json",
            "Authorization": "Bearer " + os.environ["AI_API_KEY"],
        },
    )
    with urllib.request.urlopen(req, timeout=180) as resp:
        return json.loads(resp.read())["choices"][0]["message"]["content"]


def parse_score(raw):
    text = (raw or "").strip()
    m = JSON_FENCE.search(text)
    if m:
        text = m.group(1)
    obj = json.loads(text)  # 不可解析即抛错——与 Java 侧 fail-fast 同纪律
    return {
        "accuracy": int(obj["accuracy"]),
        "compliance": int(obj["compliance"]),
        "grounding": int(obj["grounding"]),
        "reason": str(obj["reason"]),
    }


def main():
    golden = json.loads(GOLDEN.read_text(encoding="utf-8"))
    by_query = {}
    for c in golden:
        by_query.setdefault(c["query"], c)
    answers = parse_answers_from_report(LOG, SAMPLE)
    template = PROMPT_FILE.read_text(encoding="utf-8")
    assert "{corpusTimeline}" in template, "judge 模板缺时点清单占位符——处置 a 未生效？"
    timeline = load_corpus_timeline()

    results = {}
    for q in SAMPLE:
        raw = call_judge(render(template, by_query[q], answers[q], timeline))
        results[q] = parse_score(raw) | {"raw": raw}
        s = results[q]
        print(f"[{q[:18]}] {s['accuracy']}/{s['compliance']}/{s['grounding']}")

    OUT.write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"written: {OUT.name}（盲袋——Owner 打分交回前不展示内容）")


if __name__ == "__main__":
    main()
