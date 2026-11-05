#!/usr/bin/env python3
"""W28 任务1① 取证探针：案30「茅台的法定代表人是谁」四候选召回手段逐项实测。

背景（案30归因，2026-11-03 结案）：正确答案块（上市公司法定代表人 陈华，
2025年报公司信息页 + 2026半年报各一块）在库，但 dense 相似度仅排 15/16 名
（score 0.654/0.659），top-5 被语义同质的控股股东/实控人块（0.673~0.708）占据。
线上实测（2026-11-04）：改写后查询「贵州茅台（600519）的法定代表人是谁」，
hybrid 模式 recallHits=27，最终 top-5 仍漏正确块。

四候选手段（w28 计划任务1①）：
  A. top-k 扩大          —— 直接测：正确块在 dense 排名多少，需要多大 top-k
  B. 关键词混合检索       —— 本地 BM25（CJK 二元组，对齐 sparse 路 token 集）排序，
                            看正确块能否进前列；并与 dense RRF 融合看 top-5
  C. 多路召回             —— 3 条查询变体各自 dense top-10，RRF 融合看 top-5
  D. 改写联动             —— 变体即改写方向的代理证据（关键词式 / 精确字段式改写）

方法诚实声明：
- 语料：Qdrant 现网 collection 全量 scroll（分页取全），与线上同一语料快照
- dense 向量：SiliconFlow BAAI/bge-m3，与线上同一模型同一 API
- BM25 为本地复算（Okapi k1=1.5 b=0.75，CJK 二元组），近似线上 Qdrant bm25
  稀疏路排序信号；线上 sparse 路对长问句近零产出（W24 #1a 实锤），
  本探针回答的是「若 sparse 路正常工作，正确块能排第几」
- 负例零误伤与 Recall@5 全量回归在任务1② 黄金集裁决完成，本探针只管单案可解性

用法：set -a; source .env; set +a; python3 docs/scripts/w28-recall-probe.py
"""

import json
import math
import os
import re
import sys
import urllib.request
from collections import Counter

QDRANT = "http://localhost:6333"
COLLECTION = "knowledge_base"
EMBED_URL = os.environ.get("SILICONFLOW_BASE_URL", "https://api.siliconflow.cn/v1") + "/embeddings"
EMBED_MODEL = os.environ.get("AI_EMBEDDING_MODEL", "BAAI/bge-m3")
API_KEY = os.environ.get("SILICONFLOW_API_KEY", "")

QUERY_REWRITTEN = "贵州茅台（600519）的法定代表人是谁"  # 线上改写后实跑口径
QUERY_VARIANTS = [
    QUERY_REWRITTEN,
    "贵州茅台酒股份有限公司 法定代表人",  # 关键词式改写
    "贵州茅台 公司信息 法定代表人 姓名",  # 精确字段式改写
]

# 正确块判定：含「陈华」且含「法定代表人」；语义区分（上市公司 vs 控股股东）靠人工读 snippet
TARGET_MARK = "陈华"


def http_post(url, payload):
    req = urllib.request.Request(
        url,
        data=json.dumps(payload).encode(),
        headers={"Content-Type": "application/json"},
    )
    return json.load(urllib.request.urlopen(req, timeout=120))


def embed(texts):
    req = urllib.request.Request(
        EMBED_URL,
        data=json.dumps({"model": EMBED_MODEL, "input": texts}).encode(),
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {API_KEY}",
        },
    )
    resp = json.load(urllib.request.urlopen(req, timeout=120))
    return [d["embedding"] for d in resp["data"]]


def dense_search(vector, topk):
    resp = http_post(
        f"{QDRANT}/collections/{COLLECTION}/points/search",
        {"vector": vector, "limit": topk, "with_payload": True, "with_vector": False},
    )
    return [
        {
            "id": p["id"],
            "score": p["score"],
            "source": p["payload"].get("source", "?"),
            "asOf": p["payload"].get("asOf", "?"),
            "text": p["payload"].get("doc_content", ""),
        }
        for p in resp["result"]
    ]


def scroll_all():
    points, offset = [], None
    while True:
        body = {"limit": 500, "with_payload": True, "with_vector": False}
        if offset is not None:
            body["offset"] = offset
        resp = http_post(f"{QDRANT}/collections/{COLLECTION}/points/scroll", body)
        points.extend(resp["result"]["points"])
        offset = resp["result"].get("next_page_offset")
        if not offset:
            break
    return [
        {
            "id": p["id"],
            "source": p["payload"].get("source", "?"),
            "asOf": p["payload"].get("asOf", "?"),
            "text": p["payload"].get("doc_content", ""),
        }
        for p in points
    ]


def bigrams(text):
    """CJK 二元组 + ASCII 整词（与 w16 探针同 token 集，对齐线上 sparse 路）。"""
    tokens = re.findall(r"[A-Za-z0-9%.]+", text.lower())
    for run in re.findall(r"[一-鿿]+", text):
        if len(run) == 1:
            tokens.append(run)
        else:
            tokens.extend(run[i : i + 2] for i in range(len(run) - 1))
    return tokens


def bm25_rank(query, chunks, k1=1.5, b=0.75):
    doc_tokens = [bigrams(c["text"]) for c in chunks]
    n = len(chunks)
    avgdl = sum(len(t) for t in doc_tokens) / n
    df = Counter()
    for tokens in doc_tokens:
        for t in set(tokens):
            df[t] += 1
    q_tokens = bigrams(query)
    scored = []
    for i, tokens in enumerate(doc_tokens):
        tf = Counter(tokens)
        dl = len(tokens)
        score = 0.0
        for t in q_tokens:
            if t not in df:
                continue
            idf = math.log((n - df[t] + 0.5) / (df[t] + 0.5) + 1)
            score += idf * tf[t] * (k1 + 1) / (tf[t] + k1 * (1 - b + b * dl / avgdl))
        scored.append((score, chunks[i]))
    scored.sort(key=lambda x: -x[0])
    return [{"id": c["id"], "score": s, **{k: c[k] for k in ("source", "asOf", "text")}} for s, c in scored if s > 0]


def rrf(rank_lists, k=60):
    scores = Counter()
    docs = {}
    for ranked in rank_lists:
        for rank, doc in enumerate(ranked, 1):
            scores[doc["id"]] += 1.0 / (k + rank)
            docs[doc["id"]] = doc
    fused = sorted(scores.items(), key=lambda x: -x[1])
    return [{"id": i, "rrf": s, **docs[i]} for i, s in fused]


def show(title, ranked, n=8):
    print(f"\n── {title} ──")
    for rank, d in enumerate(ranked[:n], 1):
        text = re.sub(r"\s+", " ", d["text"])
        mark = " ★含陈华" if TARGET_MARK in d["text"] else ""
        has_fzr = "法定代表人" in d["text"]
        score = d.get("score", d.get("rrf", 0))
        print(f"  {rank:2d}. [{d['source']}|{d['asOf']}] {score:.4f}{mark}{' [法代]' if has_fzr else ''}")
        print(f"      {text[:90]}")


def rank_of(ranked, pred):
    for i, d in enumerate(ranked, 1):
        if pred(d):
            return i
    return None


def is_target(d):
    return TARGET_MARK in d["text"] and "法定代表人" in d["text"]


def main():
    if not API_KEY:
        sys.exit("SILICONFLOW_API_KEY 未设置")
    print("加载语料（scroll 全量）…")
    chunks = scroll_all()
    print(f"语料 {len(chunks)} 块")

    print("嵌入查询…")
    vectors = embed(QUERY_VARIANTS)

    # A：dense top-20（改写后实跑查询）
    dense = dense_search(vectors[0], 20)
    show(f"A. dense top-20（改写后查询）正确块排名={rank_of(dense, is_target)}", dense, 20)

    # B：本地 BM25
    bm = bm25_rank(QUERY_REWRITTEN, chunks)
    show(f"B. BM25 排序 正确块排名={rank_of(bm, is_target)}", bm, 10)

    # B+：dense + BM25 RRF 融合（模拟强化后的 hybrid）
    fused = rrf([dense, bm[:20]])
    show(f"B+. dense+BM25 RRF 融合 正确块排名={rank_of(fused, is_target)}", fused, 10)

    # C/D：多路召回（3 变体 dense top-10 各自搜，RRF 融合）
    per_variant = [dense_search(v, 10) for v in vectors]
    for q, r in zip(QUERY_VARIANTS, per_variant):
        print(f"\n  变体「{q}」dense top-10 正确块排名={rank_of(r, is_target)}")
    multi = rrf(per_variant)
    show("C. 多路召回 RRF 融合（3 变体）正确块排名={}".format(rank_of(multi, is_target)), multi, 10)

    print("\n══ 汇总：正确块（含陈华+法定代表人）各手段排名 ══")
    print(f"  A dense top-20        : {rank_of(dense, is_target)}")
    print(f"  B BM25                : {rank_of(bm, is_target)}")
    print(f"  B+ dense+BM25 RRF     : {rank_of(fused, is_target)}")
    print(f"  C 多路召回 RRF        : {rank_of(multi, is_target)}")


if __name__ == "__main__":
    main()
