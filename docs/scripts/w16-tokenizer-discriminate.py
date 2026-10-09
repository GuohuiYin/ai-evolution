#!/usr/bin/env python3
"""判别性查询：确定 Qdrant 服务端 BM25 的 CJK 分词规则。"""

import json
import urllib.request

BASE = "http://localhost:16333"
COLLECTION = "bm25_probe"


def query(text, limit=4):
    req = urllib.request.Request(
        f"{BASE}/collections/{COLLECTION}/points/query",
        data=json.dumps(
            {
                "query": {"text": text, "model": "qdrant/bm25"},
                "using": "bm25",
                "limit": limit,
                "with_payload": True,
            }
        ).encode(),
        headers={"Content-Type": "application/json"},
    )
    res = json.load(urllib.request.urlopen(req))
    hits = [(f"{h['score']:.3f}", h["payload"]["source"][:16]) for h in res["result"]["points"]]
    return hits if hits else "（零命中）"


# maotai.md 原文锚点：「品牌壁垒深厚，提价能力强」「12987」「贵州茅台」
for q in [
    "品",  # 单字：若逐字分词必中
    "品牌",  # 词：若词级分词必中
    "品牌壁垒深厚",  # 文中完整 run（标点界内）：若整 run 单 token 必中
    "品牌壁垒",  # run 的子串：整 run 单 token 则不中
    "茅台",  # 高频词片段
    "12987",  # ASCII 数字代号：对照组（ASCII 分词应正常）
    "129",  # 数字片段：检验数字是否整串成 token
]:
    print(f"「{q}」→ {query(q)}")
