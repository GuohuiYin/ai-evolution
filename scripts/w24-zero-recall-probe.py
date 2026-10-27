#!/usr/bin/env python3
"""W24 #1a 零召回病灶探针：dense/sparse 两腿分离读数。

对照组：
- 失败查询（W23 多轮案实录改写）：贵州茅台酿造工艺中的12987是什么含义 → recallHits=0
- 成功查询（W23 单轮案实录改写）：贵州茅台 12987 酿造工艺中每个数字分别代表什么 → recallHits=20

逐层回答：dense 腿是否全低于阈值 0.5？sparse 腿（MatchText AND 语义）是否零命中？
"""
import json
import os
import urllib.request

SILICONFLOW = "https://api.siliconflow.cn/v1/embeddings"
QDRANT = "http://localhost:6333"
COLLECTION = "knowledge_base"
THRESHOLD = 0.5

QUERIES = {
    "失败查询（粘连）": "贵州茅台酿造工艺中的12987是什么含义",
    "成功查询（带空格）": "贵州茅台 12987 酿造工艺中每个数字分别代表什么",
}


def post(url, payload, headers=None):
    req = urllib.request.Request(
        url,
        data=json.dumps(payload).encode(),
        headers={"Content-Type": "application/json", **(headers or {})},
    )
    return json.loads(urllib.request.urlopen(req).read())


def embed(text):
    key = os.environ["SILICONFLOW_API_KEY"]
    resp = post(
        SILICONFLOW,
        {"model": "BAAI/bge-m3", "input": text},
        {"Authorization": f"Bearer {key}"},
    )
    return resp["data"][0]["embedding"]


def dense_leg(text):
    vec = embed(text)
    resp = post(
        f"{QDRANT}/collections/{COLLECTION}/points/query",
        {"query": vec, "limit": 20, "with_payload": False, "score_threshold": None},
    )
    points = resp["result"]["points"] if "points" in resp.get("result", {}) else resp["result"]
    return [(p.get("score"), p["id"]) for p in points]


def sparse_leg(text):
    """复刻 QdrantSparseRecall：MatchText 过滤 doc_content，scroll 取前 20。"""
    resp = post(
        f"{QDRANT}/collections/{COLLECTION}/points/scroll",
        {
            "filter": {"must": [{"key": "doc_content", "match": {"text": text}}]},
            "limit": 20,
            "with_payload": False,
        },
    )
    return resp["result"]["points"]


def main():
    for label, q in QUERIES.items():
        print(f"\n═══ {label}: {q}")
        dense = dense_leg(q)
        scores = [s for s, _ in dense]
        above = [s for s in scores if s >= THRESHOLD]
        print(f"  dense 腿：{len(dense)} 条返回，≥{THRESHOLD} 的 {len(above)} 条，"
              f"top 分={max(scores):.4f}，分数带={['%.3f' % s for s in scores[:8]]}")
        sparse = sparse_leg(q)
        print(f"  sparse 腿（MatchText AND）：{len(sparse)} 条命中")


if __name__ == "__main__":
    main()
