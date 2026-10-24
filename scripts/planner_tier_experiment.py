#!/usr/bin/env python3
"""W22 #1a · Planner 双档对比实验（Owner 裁决①方案 B：小实验定档再进管线）

对照两臂：主力档 deepseek-v4-flash（DeepSeek 直连） vs 轻量档候选
Qwen/Qwen3.5-35B-A3B（SiliconFlow，W12 failover 在用的现成凭证面）。
样本：生成黄金集分层 10 案（2 factual / 3 compound / 2 multi-turn / 2 unanswerable / 1 redline）。

逐案记录：模型原始输出、JSON 可解析性、延迟、usage（prompt/completion tokens）。
质量人工评（子问题覆盖/通路标注/拆解粒度），报告另行撰写。

用法：set -a && source .env && set +a && python3 scripts/planner_tier_experiment.py
产出：docs/eval/selection/planner-tier-v1-samples.json
"""

import json
import os
import re
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
GOLDEN = ROOT / "src/main/resources/eval/golden-set-generation.json"
PROMPT_FILE = ROOT / "src/main/resources/prompts/planner-v1.md"
OUT = ROOT / "docs/eval/selection/planner-tier-v1-samples.json"

# 分层 10 案（与 W19-W21 校准同族口径：可复现 = 可对照）
SAMPLE = [
    "茅台 2025 年归母净利润是多少",
    "宁德时代 2024 年营业总收入是多少",
    "茅台 2024 年净利润和分红情况如何",
    "600519 2024 年营收多少？顺便查年报里的工艺描述",
    "宁德时代 2024 年归母净利润约多少，动力电池全球市占率多少",
    "中期分红算在里面吗",
    "它的净利润呢",
    "茅台冰淇淋业务的营收占比是多少",
    "宁德时代固态电池什么时候量产",
    "宁德时代的股票现在值得买入吗",
]

ARMS = {
    "deepseek-v4-flash": {
        "base": os.environ.get("AI_BASE_URL", "https://api.deepseek.com"),
        "key": os.environ["AI_API_KEY"],
        "model": os.environ.get("AI_MODEL", "deepseek-v4-flash"),
    },
    "qwen3.5-35b-a3b": {
        "base": os.environ.get("SILICONFLOW_BASE_URL", "https://api.siliconflow.cn/v1"),
        "key": os.environ["SILICONFLOW_API_KEY"],
        "model": "Qwen/Qwen3.5-35B-A3B",
    },
}

JSON_FENCE = re.compile(r"```(?:json)?\s*(\{.*?})\s*```", re.DOTALL)
CHANNELS = {"rag", "stock", "announcement", "none"}


def call(arm, prompt, timeout=300, retries=2):
    body = json.dumps(
        {"model": arm["model"], "messages": [{"role": "user", "content": prompt}]}
    ).encode("utf-8")
    req = urllib.request.Request(
        arm["base"] + "/chat/completions",
        data=body,
        headers={
            "Content-Type": "application/json",
            "Authorization": "Bearer " + arm["key"],
        },
    )
    last = None
    for attempt in range(1, retries + 1):
        t0 = time.time()
        try:
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                payload = json.loads(resp.read())
            elapsed = time.time() - t0
            choice = payload["choices"][0]["message"]["content"]
            usage = payload.get("usage", {})
            return choice, elapsed, usage
        except Exception as e:  # 实验脚本：超时/瞬断重试并留痕，不静默兜底
            last = e
            print(f"  retry {attempt}/{retries}: {type(e).__name__}: {e}")
            time.sleep(5)
    raise last


def validate_plan(raw):
    """返回 (parsed|None, 问题清单)——可解析性+schema+通路合法性的机械校验。"""
    text = (raw or "").strip()
    m = JSON_FENCE.search(text)
    if m:
        text = m.group(1)
    problems = []
    try:
        obj = json.loads(text)
    except json.JSONDecodeError:
        return None, ["JSON 不可解析"]
    subs = obj.get("subQuestions")
    if not isinstance(subs, list) or not subs:
        problems.append("subQuestions 缺失或为空")
        return obj, problems
    for i, s in enumerate(subs):
        if not isinstance(s, dict) or not s.get("q") or s.get("channel") not in CHANNELS:
            problems.append(f"子问题 {i} schema 不合规: {s}")
    return obj, problems


def main():
    golden = json.loads(GOLDEN.read_text(encoding="utf-8"))
    by_query = {}
    for c in golden:
        by_query.setdefault(c["query"], c)
    template = PROMPT_FILE.read_text(encoding="utf-8")
    assert "{history}" in template and "{query}" in template

    results = {}
    for q in SAMPLE:
        case = by_query[q]
        turns = case.get("turns") or []
        history = "\n".join(turns[:-1]) if turns else ""
        prompt = template.replace("{history}", history or "（空）").replace("{query}", q)
        results[q] = {"category": case["category"], "turns": turns}
        for arm_name, arm in ARMS.items():
            raw, elapsed, usage = call(arm, prompt)
            plan, problems = validate_plan(raw)
            results[q][arm_name] = {
                "raw": raw,
                "elapsed_sec": round(elapsed, 2),
                "prompt_tokens": usage.get("prompt_tokens"),
                "completion_tokens": usage.get("completion_tokens"),
                "parse_ok": not problems,
                "problems": problems,
                "plan": plan,
            }
            print(f"[{q[:14]}|{arm_name[:12]}] parse={'OK' if not problems else problems} "
                  f"{elapsed:.1f}s in={usage.get('prompt_tokens')} out={usage.get('completion_tokens')}")

    OUT.write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"written: {OUT.name}")


if __name__ == "__main__":
    main()
