#!/usr/bin/env python3
"""W26 #2 合成流量新题（裁决①C 混合流量的「合成补密度」一半）。

做法：以黄金集各类别种子题为风格样本，让 DeepSeek 按类别批量仿写新题，
归一化查重（对黄金集 + 批内）后落盘 scripts/w26-synthetic-traffic.json。

边界声明：产出只作 24h 仿真流量素材，**不进入黄金集**、不参与评估——
评估资产的新增必须走造数规范（docs/eval/golden-set-authoring.md）人工裁定。

用法：
  set -a; source .env; set +a
  python3 scripts/w26_synthesize_traffic.py            # 生成并落盘
  python3 scripts/w26_synthesize_traffic.py --dry-run  # 只打印不落盘
"""

import argparse
import json
import os
import re
import sys
import urllib.request
from pathlib import Path

GOLDEN = Path("src/main/resources/eval/golden-set-generation.json")
OUT = Path("scripts/w26-synthetic-traffic.json")

# 目标分布：对齐黄金集 58 案的类别形状（factual 19 / multi-turn 13 / compound 11 / 不可答 8 / 红线 7）
TARGETS = {"factual": 20, "multi-turn": 13, "compound": 11, "in-domain-unanswerable": 8, "redline": 8}

CATEGORY_GUIDE = {
    "factual": "库内可查的个股事实问答（茅台/宁德时代的年报、半年报披露数据与业务描述）",
    "multi-turn": "需要结合上下文的多轮追问式单题（含指代：它/该公司/那家）",
    "compound": "复合问：一问里要两个以上事实或跨期对比",
    "in-domain-unanswerable": "域内但库内无答案的问题（未覆盖的公司/期间/口径），应触发拒答",
    "redline": "诱导买卖建议/目标价/涨跌预测的红线问题，应触发拒答",
}


def normalize(text: str) -> str:
    """查重归一化：小写 + 去全部空白与标点，只看字面骨架。"""
    return re.sub(r"[\s，。？！、,.?!；;：:\"'""'']+", "", text.strip().lower())


def call_deepseek(prompt: str) -> str:
    key = os.environ.get("AI_API_KEY")
    if not key:
        sys.exit("缺 AI_API_KEY（先 set -a; source .env; set +a）")
    base = os.environ.get("AI_BASE_URL", "https://api.deepseek.com").rstrip("/")
    model = os.environ.get("AI_MODEL", "deepseek-v4-flash")
    payload = json.dumps({
        "model": model,
        "messages": [{"role": "user", "content": prompt}],
        "temperature": 1.0,  # 造题要多样性，温度拉高
    }).encode()
    req = urllib.request.Request(
        f"{base}/chat/completions",
        data=payload,
        headers={"Content-Type": "application/json", "Authorization": f"Bearer {key}"},
    )
    with urllib.request.urlopen(req, timeout=120) as resp:
        return json.loads(resp.read().decode())["choices"][0]["message"]["content"]


def extract_json_array(text: str) -> list:
    match = re.search(r"\[.*\]", text, re.DOTALL)
    if not match:
        return []
    try:
        return json.loads(match.group(0))
    except json.JSONDecodeError:
        return []


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    golden = json.loads(GOLDEN.read_text(encoding="utf-8"))
    seen = {normalize(item["query"]) for item in golden}

    result = []
    for category, count in TARGETS.items():
        seeds = [x["query"] for x in golden if x["category"] == category][:4]
        prompt = (
            f"你是个股研究问答系统的测试造题器。请仿照种子题的风格，生成 {count} 条全新的中文用户提问。\n"
            f"类别：{category}——{CATEGORY_GUIDE[category]}\n"
            f"种子题（风格参照，禁止照抄或只改一两个字）：\n"
            + "\n".join(f"- {s}" for s in seeds)
            + "\n要求：只输出 JSON 数组（字符串数组），不要解释；题目之间要有多样性（换公司/期间/角度）。"
        )
        raw = call_deepseek(prompt)
        items = extract_json_array(raw)
        added = 0
        for q in items:
            if isinstance(q, str) and 5 <= len(q) <= 80 and normalize(q) not in seen:
                seen.add(normalize(q))
                result.append({"query": q.strip(), "category": category, "origin": "synthetic-w26"})
                added += 1
        print(f"{category}: 目标 {count}，实收 {added}")

    print(f"合计 {len(result)} 条")
    if not args.dry_run:
        OUT.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"已落盘 {OUT}")


if __name__ == "__main__":
    main()
