#!/usr/bin/env python3
"""W16 #2 取证第一刀：真 BM25 稀疏权重排序 vs 品牌壁垒案可解性验证。

背景：品牌壁垒案（"这两家公司里谁的品牌壁垒更厚" → 改写后"贵州茅台和宁德时代哪家的
品牌壁垒更厚"，期望 maotai.md）在现状管线（dense 阈值 0.5 + sparse MatchText 候选网 +
SparseCoverage 0.5 覆盖率硬裁决）下双路未召回。取证问题：换真 BM25 排序（TF-IDF +
长度归一，无硬阈值），该案的期望文档能否进 Top-5？

方法诚实声明：
- 分词用 CJK 二元组（与现状 sparse 路同 token 集）——隔离的变量只有"BM25 排序信号
  vs 覆盖率硬裁决"，正是取证②定位的根因；非中文词表分词（jieba）环境不可得，此近似
  对结论方向保守（二元组噪声只会压低区分度）
- 语料：Qdrant 现网 collection 全量 303 chunks（scroll 导出，含 PDF 年报 301 条），
  与线上 eval 同一语料快照
- 公式：Okapi BM25，k1=1.5，b=0.75，IDF = ln((N-df+0.5)/(df+0.5)+1)

用法：python3 docs/scripts/w16-bm25-probe.py [chunks.json]
（chunks.json 缺省从 localhost:6333 scroll 现拉）
"""

import json
import math
import re
import sys
import urllib.request
from collections import Counter

COLLECTION = "knowledge_base"
QDRANT = "http://localhost:6333"

# 挂账 3 案（W15 挂账承接）：改写后查询（eval 实跑口径）+ 原始查询
CASES = [
    ("品牌壁垒（改写后）", "贵州茅台和宁德时代哪家的品牌壁垒更厚", "maotai.md"),
    ("品牌壁垒（原始）", "这两家公司里谁的品牌壁垒更厚", "maotai.md"),
    ("碳酸锂（改写后）", "碳酸锂价格波动对宁德时代的影响", "catl.md"),
    ("赤水河（改写后）", "贵州茅台 赤水河流域环境容量约束", "maotai.md"),
]

CJK = re.compile(r"[一-鿿]")


def bigrams(text):
    """CJK 二元组 + ASCII 整词（对齐现状 SparseCoverage 的 token 集）。"""
    tokens = re.findall(r"[A-Za-z0-9%.]+", text.lower())
    cjk_runs = re.findall(r"[一-鿿]+", text)
    for run in cjk_runs:
        if len(run) == 1:
            tokens.append(run)
        else:
            tokens.extend(run[i : i + 2] for i in range(len(run) - 1))
    return tokens


def load_chunks(path=None):
    if path:
        data = json.load(open(path))
    else:
        req = urllib.request.Request(
            f"{QDRANT}/collections/{COLLECTION}/points/scroll",
            data=json.dumps(
                {"limit": 500, "with_payload": True, "with_vector": False}
            ).encode(),
            headers={"Content-Type": "application/json"},
        )
        data = json.load(urllib.request.urlopen(req))
    return [
        {
            "source": p["payload"].get("source", "?"),
            "text": p["payload"].get("doc_content", ""),
        }
        for p in data["result"]["points"]
    ]


def bm25_rank(query, chunks, k1=1.5, b=0.75):
    doc_tokens = [bigrams(c["text"]) for c in chunks]
    n = len(chunks)
    avgdl = sum(len(t) for t in doc_tokens) / n
    df = Counter()
    for tokens in doc_tokens:
        for t in set(tokens):
            df[t] += 1
    q_tokens = bigrams(query)
    scores = []
    for i, tokens in enumerate(doc_tokens):
        tf = Counter(tokens)
        dl = len(tokens)
        score = 0.0
        for t in q_tokens:
            if t not in df:
                continue
            idf = math.log((n - df[t] + 0.5) / (df[t] + 0.5) + 1)
            f = tf.get(t, 0)
            score += idf * f * (k1 + 1) / (f + k1 * (1 - b + b * dl / avgdl))
        scores.append(score)
    return sorted(enumerate(scores), key=lambda x: -x[1])


def main():
    chunks = load_chunks(sys.argv[1] if len(sys.argv) > 1 else None)
    print(f"语料：{len(chunks)} chunks（现网 {COLLECTION} scroll 快照）\n")
    verdicts = []
    for name, query, expect in CASES:
        ranked = bm25_rank(query, chunks)
        top5 = [(chunks[i]["source"], s) for i, s in ranked[:5]]
        hit_rank = next(
            (r + 1 for r, (i, _) in enumerate(ranked) if chunks[i]["source"] == expect),
            None,
        )
        top1_src, top1_score = top5[0]
        second_score = top5[1][1] if len(top5) > 1 else 0.0
        print(f"▶ {name}：{query}")
        print(f"  期望={expect} 命中排名={hit_rank or '未进榜'}")
        for rank, (src, s) in enumerate(top5, 1):
            mark = " ← 期望" if src == expect else ""
            print(f"  Top{rank} {s:7.2f} {src}{mark}")
        gap = top1_score - second_score
        print(f"  Top1-Top2 分差={gap:.2f}\n")
        verdicts.append((name, hit_rank))
    print("══ 可解性结论 ══")
    for name, rank in verdicts:
        print(f"  {name}：{'可解（Top' + str(rank) + '）' if rank and rank <= 5 else '不可解'}")


if __name__ == "__main__":
    main()
