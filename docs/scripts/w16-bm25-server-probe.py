#!/usr/bin/env python3
"""W16 #2 A1 路径关键未知数 probe：Qdrant 服务端 BM25 的中文分词行为。

探针容器：qdrant/qdrant:v1.18.2 @ localhost:16333（独立数据卷，不碰生产 knowledge_base）。
观测三件事：
  1. Document{text, model:"qdrant/bm25"} 推理对象自托管是否可用（≥1.15.2 口径）
  2. 中文分词粒度——回读稀疏向量 indices 数量：整 run 单 token / 逐字 / 词级 / 二元组
  3. 端到端行为：真实 chunk upsert 后查询"品牌壁垒"，maotai chunk 能否 Top1

输入：/tmp/w16-chunks.json（现网 303 chunks scroll 快照，取 maotai/catl 各 1 + 年报 2 条）
"""

import json
import sys
import urllib.request

BASE = "http://localhost:16333"
COLLECTION = "bm25_probe"


def call(method, path, body=None):
    req = urllib.request.Request(
        f"{BASE}{path}",
        method=method,
        data=json.dumps(body).encode() if body is not None else None,
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req) as resp:
        return json.load(resp)


def main(chunks_path):
    chunks = json.load(open(chunks_path))["result"]["points"]
    picked = []
    for p in chunks:
        src = p["payload"].get("source", "?")
        if src == "maotai.md" or src == "catl.md":
            picked.append(p)
        elif len([x for x in picked if x["payload"].get("source", "").endswith(".pdf")]) < 2:
            picked.append(p)
    print(f"探针语料：{len(picked)} 条（maotai/catl 全量 chunk + 年报抽样 2 条）")

    # 1. 建 collection：sparse 命名向量 + IDF modifier（服务端维护 IDF）
    call(
        "PUT",
        f"/collections/{COLLECTION}",
        {"sparse_vectors": {"bm25": {"modifier": "idf"}}},
    )
    print("① collection 建好（sparse 'bm25' + modifier=idf）")

    # 2. Document 推理对象 upsert——服务端 BM25 转换（自托管可用性的直接验证）
    avg_len = 800  # BM25 长度归一参数：对齐 chunk-size 配置量级
    points = [
        {
            "id": i + 1,
            "vector": {
                "bm25": {
                    "text": p["payload"]["doc_content"],
                    "model": "qdrant/bm25",
                    "options": {"avg_len": avg_len},
                }
            },
            "payload": {"source": p["payload"].get("source", "?")},
        }
        for i, p in enumerate(picked)
    ]
    call("PUT", f"/collections/{COLLECTION}/points", {"points": points})
    print("② Document 推理对象 upsert 成功——服务端 BM25 自托管可用 ✓")

    # 3. 回读稀疏向量，数 indices 看分词粒度
    got = call(
        "POST",
        f"/collections/{COLLECTION}/points/scroll",
        {"limit": 10, "with_vector": True, "with_payload": True},
    )
    print("③ 分词粒度观测（每条 chunk 的稀疏项数 vs 文本长度）：")
    for pt in got["result"]["points"]:
        text_len = len(picked[pt["id"] - 1]["payload"]["doc_content"])
        n_indices = len(pt["vector"]["bm25"]["indices"])
        src = pt["payload"]["source"]
        print(f"   id={pt['id']} {src[:20]:22} 文本 {text_len:4} 字 → 稀疏项 {n_indices:4}")

    # 4. 端到端查询：锚定词 + 改写后全句 + 原始问句
    for query in [
        "品牌壁垒",
        "贵州茅台和宁德时代哪家的品牌壁垒更厚",
        "这两家公司里谁的品牌壁垒更厚",
    ]:
        res = call(
            "POST",
            f"/collections/{COLLECTION}/points/query",
            {
                "query": {"text": query, "model": "qdrant/bm25"},
                "using": "bm25",
                "limit": 4,
                "with_payload": True,
            },
        )
        print(f"④ 查询「{query}」：")
        for hit in res["result"]["points"]:
            print(f"   {hit['score']:8.3f} {hit['payload']['source']}")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "/tmp/w16-chunks.json")
