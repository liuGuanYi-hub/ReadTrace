# -*- coding: utf-8 -*-
"""
验证 MindprintConstellationView 的三层配额策略在真实数据下的分布情况。
严格按 Kotlin 侧逻辑复刻：
  1) 同媒介主星连线（骨架）—— 不占跨媒介配额
  2) 跨媒介弦：按媒介对分组 -> 组内轮转录取（每轮每书最多 1 条）-> 余量还给全局
  3) 每书上限 MAX_EDGES_PER_BOOK
"""
import json
import os
import itertools
from collections import defaultdict

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(BASE, "912_filled.json")

MAX_CROSS_MEDIA_EDGES = 36
MAX_EDGES_PER_BOOK = 3
CROSS_MEDIA_MIN_SIMILARITY = 80
MAJOR_STAR_TOP_N = 6
MAJOR_STAR_MIN_AVG = 8.8
FULL_SCALE = 2.6


def has_mp(b):
    mp = b.get("mindprint")
    if not mp:
        return False
    keys = ["depthScore", "artistryScore", "emotionScore",
            "logicScore", "difficultyScore", "healingScore"]
    return any(float(mp.get(k) or 0) > 0 for k in keys)


def vec(b):
    mp = b["mindprint"]
    return [
        float(mp.get("depthScore") or 8.0),
        float(mp.get("artistryScore") or 8.0),
        float(mp.get("emotionScore") or 8.0),
        float(mp.get("logicScore") or 8.0),
        float(mp.get("difficultyScore") or 5.0),
        float(mp.get("healingScore") or 8.0),
    ]


def avg_score(b):
    v = vec(b)
    return sum(v) / len(v)


def sim(a, b):
    """与 ResonancePosterActivity.calculateSimilarity 同刻度：五维（不含 difficulty）"""
    va, vb = vec(a), vec(b)
    idx = [0, 1, 2, 3, 5]
    diff = sum(abs(va[i] - vb[i]) for i in idx)
    return int(max(65, min(99, 99.0 - (diff / FULL_SCALE) * 30.0)))


def main():
    with open(SRC, "r", encoding="utf-8") as f:
        raw = json.load(f)

    # 实际结构：{ app, version, works: [...] }
    if isinstance(raw, dict):
        books = raw.get("works") or raw.get("books") or raw.get("data") or []
    else:
        books = raw

    total = len(books)
    withmp = [b for b in books if has_mp(b)]
    print(f"总作品数: {total}")
    print(f"含有效 mindprint: {len(withmp)}")
    without = [b for b in books if not has_mp(b)]
    print(f"缺 mindprint: {len(without)}")
    if without:
        print("  缺档案样例:", [b.get("title") for b in without[:8]])

    # 媒体分布
    by_media = defaultdict(list)
    for b in withmp:
        by_media[b.get("mediaType", "?")].append(b)
    print("\n各媒介档案数:", dict((k, len(v)) for k, v in sorted(by_media.items())))

    # 主星 = 每媒介 rating 排名前 MAJOR_STAR_TOP_N 或六维均分 >= MAJOR_STAR_MIN_AVG
    stars = []
    for m, lst in by_media.items():
        lst_sorted = sorted(lst, key=lambda x: (-(x.get("rating") or 0), x.get("title") or ""))
        for i, b in enumerate(lst_sorted):
            if i < MAJOR_STAR_TOP_N or avg_score(b) >= MAJOR_STAR_MIN_AVG:
                stars.append(b)
    print(f"主星总数: {len(stars)}  (top{MAJOR_STAR_TOP_N} + avg>={MAJOR_STAR_MIN_AVG})")

    # 全量跨媒介候选对
    cand = []
    for a, b in itertools.combinations(stars, 2):
        if a.get("mediaType") == b.get("mediaType"):
            continue
        s = sim(a, b)
        if s >= CROSS_MEDIA_MIN_SIMILARITY:
            cand.append((a, b, s))
    print(f"跨媒介候选对(阈值{CROSS_MEDIA_MIN_SIMILARITY}): {len(cand)}")

    if not cand:
        print("!! 无候选，策略不会产生任何跨媒介弦")
        return

    # 按媒介对分组
    pairs = defaultdict(list)
    for a, b, s in cand:
        key = "×".join(sorted([a.get("mediaType"), b.get("mediaType")]))
        pairs[key].append((a, b, s))

    print("\n候选按媒介对分布:")
    for k in sorted(pairs, key=lambda x: -len(pairs[x])):
        print(f"  {k:24s} {len(pairs[k]):5d}")

    pair_count = len(pairs)
    per_pair_quota = (MAX_CROSS_MEDIA_EDGES + pair_count - 1) // pair_count
    print(f"\n媒介对数量: {pair_count}  每对配额: {per_pair_quota}")

    # 模拟录取：与 Kotlin 侧完全一致
    global_cnt = defaultdict(int)
    result = defaultdict(int)
    taken_total = 0
    used = set()

    # 关键：按候选数「升序」处理，稀有媒介组合优先（避免书籍额度被大组抢光）
    for key in sorted(pairs, key=lambda x: len(pairs[x])):
        lst = sorted(pairs[key], key=lambda t: -t[2])
        taken = 0
        progress = True
        while taken < per_pair_quota and progress:
            progress = False
            if taken_total >= MAX_CROSS_MEDIA_EDGES:
                break
            touch = set()
            picked = []
            for a, b, s in lst:
                if taken >= per_pair_quota or taken_total >= MAX_CROSS_MEDIA_EDGES:
                    break
                ka = (a.get("mediaType"), a.get("title"))
                kb = (b.get("mediaType"), b.get("title"))
                if ka in touch or kb in touch:
                    continue
                if global_cnt[ka] >= MAX_EDGES_PER_BOOK or global_cnt[kb] >= MAX_EDGES_PER_BOOK:
                    continue
                touch.add(ka)
                touch.add(kb)
                picked.append((ka, kb, s))
                lst.remove((a, b, s))
                taken += 1
                taken_total += 1
                progress = True
            for ka, kb, s in picked:
                global_cnt[ka] += 1
                global_cnt[kb] += 1
                used.add(tuple(sorted([ka, kb])))
                result[key] += 1

    print(f"\n最终录取跨媒介弦: {taken_total} / {MAX_CROSS_MEDIA_EDGES}")
    print("按媒介对分布:")
    for k in sorted(result, key=lambda x: -result[x]):
        print(f"  {k:24s} {result[k]}")

    # 参与配对的书籍数
    all_books = [(b.get("mediaType"), b.get("title")) for b in withmp if b.get("mediaType") == "book"]
    book_involved = set()
    for (ma, ta), (mb, tb) in used:
        if ma == "book":
            book_involved.add((ma, ta))
        if mb == "book":
            book_involved.add((mb, tb))
    print(f"\nbook 类参与配对: {len(book_involved)} / {len(all_books)}")
    print("  " + ", ".join(t for _, t in sorted(book_involved)))

    # book×anime 专项（跨媒介双生微卡入口依赖此组合）
    ba = result.get("anime×book", 0) + result.get("book×anime", 0)
    print(f"\n[关键] book×anime 弦数: {ba}  （跨媒介双生微卡入口依赖此组合，改前为 0~3）")
    for (ma, ta), (mb, tb) in sorted(used):
        pair = sorted([ma, mb])
        if pair == ["anime", "book"]:
            print(f"    - {ta}  ×  {tb}")

    # 每书度数分布
    deg_dist = defaultdict(int)
    for k, v in global_cnt.items():
        deg_dist[v] += 1
    print("度数分布:", dict(sorted(deg_dist.items())))

    # 抽样打印
    print("\n录取样例（前 16 条）:")
    for i, e in enumerate(list(used)[:16]):
        (ma, ta), (mb, tb) = e
        print(f"  {i+1:2d}. [{ma}] {ta}  ×  [{mb}] {tb}")


if __name__ == "__main__":
    main()
