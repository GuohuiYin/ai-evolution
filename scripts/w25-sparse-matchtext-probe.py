#!/usr/bin/env python3
"""W25 #4-i sparse 腿零产出根因探针：MatchText 分词与 AND 语义行为实测。

W24 #1a 已坐实「长问句 sparse 零产出」，但未拆开是哪一环：
multilingual 分词器把查询切成什么？MatchText 是 AND 全部命中还是别的口径？
本探针用分解梯（从全句逐级砍到单词）+ 交叉对照（双词同文档/跨文档）定位断点。

产出：docs/eval/retrieval/w25-sparse-tokenizer-probe.json
"""
import json
import urllib.request
from pathlib import Path

QDRANT = "http://localhost:6333"
COLLECTION = "knowledge_base"
OUT = Path(__file__).resolve().parent.parent / "docs/eval/retrieval/w25-sparse-tokenizer-probe.json"

# 分解梯：W24 涉案失败查询 → 逐级拆解 → 单词
LADDER = [
    "贵州茅台酿造工艺中的12987是什么含义",   # 原失败查询（粘连全句）
    "贵州茅台 12987 酿造工艺",              # 带空格三词
    "茅台 12987",                          # 双词（同文档共存）
    "12987 含义",                          # 双词（含义 未必与 12987 同文档）
    "贵州茅台酿造工艺",                     # 长 CJK 串（无空格）
    "茅台 碳酸锂",                          # 跨文档双词（无任一文档同含）→ AND 判据
    "12987",                               # 单代号
    "茅台",                                # 单词
]


def match_count(text):
    payload = {
        "filter": {"must": [{"key": "doc_content", "match": {"text": text}}]},
        "limit": 20,
        "with_payload": False,
    }
    req = urllib.request.Request(
        f"{QDRANT}/collections/{COLLECTION}/points/scroll",
        data=json.dumps(payload).encode(),
        headers={"Content-Type": "application/json"},
    )
    resp = json.loads(urllib.request.urlopen(req).read())
    return len(resp["result"]["points"])


def main():
    results = {}
    for q in LADDER:
        n = match_count(q)
        results[q] = n
        print(f"{n:>3} 命中 ← {q}")
    OUT.write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"written: {OUT.name}")


if __name__ == "__main__":
    main()
