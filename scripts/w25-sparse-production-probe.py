#!/usr/bin/env python3
"""W25 #4-i 补正探针：生产语义复刻版 sparse 腿行为实测。

w24-zero-recall-probe 与本日 w25-sparse-matchtext-probe 测的都是「整句裸 MatchText」
（服务端 AND），但生产 sparse 腿（W13 #2b 起）是另一条语义链：
  应用侧分词（标识符整词 + CJK 二元组）→ 逐词 MatchText should(OR) 候选网（上限 500）
  → SparseCoverage 应用侧裁决（标识符全部子串命中 / 整 token 命中 / 二元组覆盖率≥0.5）
  → 空收且有标识符时全扫兜底（仍限前 500 条）
本探针逐环节复刻该链（SparseCoverage.score 逐行对齐），坐实长问句/代号案死在哪一环。

产出：docs/eval/retrieval/w25-sparse-production-probe.json
"""
import json
import re
import urllib.request
from pathlib import Path

QDRANT = "http://localhost:6333"
COLLECTION = "knowledge_base"
OUT = Path(__file__).resolve().parent.parent / "docs/eval/retrieval/w25-sparse-production-probe.json"

QUERIES = [
    "贵州茅台酿造工艺中的12987是什么含义",          # W24 涉案失败查询（粘连）
    "贵州茅台 12987 酿造工艺中每个数字分别代表什么",  # W24 成功变体（带空格）
    "贵州茅台和宁德时代哪家的品牌壁垒更厚",           # W16 双实体覆盖率悬崖案
    "宁德时代 2024 年营业总收入",                    # 常规短查询（对照）
    "比亚迪刀片电池的技术参数",                      # adversarial 负例（期望 0）
]

CJK = r"\u4e00-\u9fff"


def normalize(text):  # FullTextNormalizer v1 复刻
    text = re.sub(rf"([{CJK}])([A-Za-z0-9%])", r"\1 \2", text)
    return re.sub(rf"([A-Za-z0-9%])([{CJK}])", r"\1 \2", text)


def runs(text):
    return [p for p in re.split(r"[\s，。、？！；：（）\"“”‘’—…]+", text) if p]


def is_identifier(t):
    return any(c.isascii() and c.isalnum() for c in t)


def bigrams(token):
    return [token[i:i + 2] for i in range(len(token) - 1)] if len(token) >= 2 else []


def query_tokens(normalized):  # SparseCoverage.queryTokens：保序去重
    seen, out = set(), []
    for t in runs(normalized):
        if t not in seen:
            seen.add(t)
            out.append(t)
    return out


def net_tokens(tokens):  # QdrantSparseRecall.netTokens：标识符 + CJK 二元组，上限 24
    net = [t for t in tokens if is_identifier(t)]
    for t in tokens:
        if not is_identifier(t):
            net += bigrams(t)
    return net[:24]


def score(normalized, doc_text):  # SparseCoverage.score 逐行对齐；负分=拒
    doc = doc_text.lower()
    tokens = query_tokens(normalized)
    identifiers = [t for t in tokens if is_identifier(t)]
    cjk = [t for t in tokens if not is_identifier(t)]
    id_hits = sum(1 for t in identifiers if t.lower() in doc)
    if identifiers and id_hits < len(identifiers):
        return -1
    whole_hits = sum(1 for t in cjk if t in doc)
    bgrams = [b for t in cjk for b in bigrams(t)]
    bgram_hits = sum(1 for b in bgrams if b in doc)
    coverage = bgram_hits / len(bgrams) if bgrams else 0
    if identifiers:
        return id_hits * 10 + whole_hits * 2 + coverage
    if whole_hits > 0:
        return whole_hits * 2 + coverage
    return coverage if coverage >= 0.5 else -1


def scroll(filter_clause, limit=500, with_payload=True):
    """翻页取尽（镜像 W25 #4 修复后的生产 fetch：按游标翻页，总量触顶 10000 截断）。"""
    points, offset = [], None
    while True:
        payload = {"limit": limit, "with_payload": with_payload}
        if filter_clause is not None:
            payload["filter"] = filter_clause
        if offset is not None:
            payload["offset"] = offset
        req = urllib.request.Request(
            f"{QDRANT}/collections/{COLLECTION}/points/scroll",
            data=json.dumps(payload).encode(),
            headers={"Content-Type": "application/json"},
        )
        result = json.loads(urllib.request.urlopen(req).read())["result"]
        points += result["points"]
        offset = result.get("next_page_offset")
        if offset is None or len(points) >= 10000:
            return points


def probe(query):
    normalized = normalize(query)
    tokens = query_tokens(normalized)
    net = net_tokens(tokens)
    should = [{"key": "doc_content", "match": {"text": t}} for t in net]
    candidates = scroll({"should": should}) if should else []
    ranked = [(p["payload"].get("source", "?"), score(normalized, p["payload"]["doc_content"]))
              for p in candidates]
    passed = sorted([(s, sc) for s, sc in ranked if sc >= 0], key=lambda x: -x[1])
    fallback_used = False
    if not passed and any(is_identifier(t) for t in tokens):
        fallback_used = True
        all_docs = scroll(None)
        ranked = [(p["payload"].get("source", "?"), score(normalized, p["payload"]["doc_content"]))
                  for p in all_docs]
        passed = sorted([(s, sc) for s, sc in ranked if sc >= 0], key=lambda x: -x[1])
    return {
        "normalized": normalized,
        "tokens": tokens,
        "net_token_count": len(net),
        "net_candidates": len(candidates),
        "fallback_used": fallback_used,
        "passed": len(passed),
        "top": passed[:5],
    }


def main():
    results = {}
    for q in QUERIES:
        r = probe(q)
        results[q] = r
        print(f"═══ {q}")
        print(f"    规整化: {r['normalized']}")
        print(f"    tokens={r['tokens']}")
        print(f"    候选网 {r['net_candidates']} 条 → 裁决通过 {r['passed']} 条"
              f"{'（走了全扫兜底）' if r['fallback_used'] else ''} → top={r['top'][:3]}")
    OUT.write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"written: {OUT.name}")


if __name__ == "__main__":
    main()
