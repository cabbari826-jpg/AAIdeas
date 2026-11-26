"""
Harder level generator: prefers long words and the curated lists, allows at most MAX_TWO 2-letter words,
and never reuses answers given in EXCLUDE. Big dictionary words (no clue yet) may fill
at most DICT_MAX slots; their clues are written by hand afterwards.

usage: EXCLUDE=A,B DICT=path/to/candidates_az.txt python3 gen_hard.py ROWS COLS SEED [TRIES]
"""
import os, random, sys, json, itertools
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from words import WORDS as EASY
from words_hard import WORDS_HARD as HARD

ALPHA = set("ABCÇDEƏFGĞHXIİJKQLMNOÖPRSŞTUÜVYZ")
EXCLUDE = set(filter(None, os.environ.get("EXCLUDE", "").split(",")))
DICT_MAX = int(os.environ.get("DICT_MAX", "3"))
MINLEN, MAXLEN = 2, 8
MAX_TWO = int(os.environ.get("MAX_TWO", "2"))

tier = {}  # word -> 0 hard curated, 1 easy curated, 2 dictionary
for w in EASY: tier[w] = 1
for w in HARD: tier[w] = 0
if os.environ.get("DICT"):
    for line in open(os.environ["DICT"], encoding="utf8"):
        w = line.strip()
        if w and not w.startswith("#") and w not in tier and 4 <= len(w) <= MAXLEN and set(w) <= ALPHA:
            tier[w] = 2
for w in list(tier):
    if w in EXCLUDE or not (MINLEN <= len(w) <= MAXLEN): del tier[w]

by_len, index = {}, {}
for w in tier:
    by_len.setdefault(len(w), set()).add(w)
    for i, ch in enumerate(w): index.setdefault((len(w), i, ch), set()).add(w)

def slots(pat):
    R, C = len(pat), len(pat[0]); out = []
    for r in range(R):
        c = 0
        while c < C:
            if pat[r][c] == '.':
                s = c
                while c < C and pat[r][c] == '.': c += 1
                if c - s >= 2: out.append(('R', r, s - 1, [(r, x) for x in range(s, c)]))
            else: c += 1
    for c in range(C):
        r = 0
        while r < R:
            if pat[r][c] == '.':
                s = r
                while r < R and pat[r][c] == '.': r += 1
                if r - s >= 2: out.append(('D', s - 1, c, [(y, c) for y in range(s, r)]))
            else: r += 1
    return out

def ok_pattern(pat, sl):
    cov = set(p for s in sl for p in s[3])
    if any(pat[r][c] == '.' and (r, c) not in cov for r in range(len(pat)) for c in range(len(pat[0]))): return False
    if sum(len(s[3]) == 2 for s in sl) > MAX_TWO: return False
    return all(MINLEN <= len(s[3]) <= MAXLEN for s in sl)

def fill(sl, rnd, budget):
    grid, used, assign = {}, set(), {}
    dict_used = [0]
    def cands(s):
        L = len(s[3]); res = None
        for i, p in enumerate(s[3]):
            if p in grid:
                b = index.get((L, i, grid[p]), set())
                res = b if res is None else res & b
                if not res: return []
        res = list(by_len.get(L, set()) if res is None else res)
        return [w for w in res if w not in used and (tier[w] < 2 or dict_used[0] < DICT_MAX)]
    def bt():
        budget[0] -= 1
        if budget[0] < 0: return False
        todo = [i for i in range(len(sl)) if i not in assign]
        if not todo: return True
        best = None
        for i in todo:
            cs = cands(sl[i])
            if not cs: return False
            if best is None or len(cs) < len(best[1]): best = (i, cs)
        i, cs = best
        rnd.shuffle(cs); cs.sort(key=lambda w: tier[w])
        for w in cs[:40]:
            prev = {p: grid.get(p) for p in sl[i][3]}
            for k, p in enumerate(sl[i][3]): grid[p] = w[k]
            assign[i] = w; used.add(w); dict_used[0] += tier[w] == 2
            if bt(): return True
            dict_used[0] -= tier[w] == 2; used.discard(w); del assign[i]
            for p, v in prev.items():
                if v is None: grid.pop(p, None)
                else: grid[p] = v
        return False
    return (grid, assign) if bt() else None

def score(sl, assign):
    ws = list(assign.values())
    return sum(len(w) for w in ws) / len(ws) + 0.3 * sum(tier[w] == 0 for w in ws) - 0.5 * sum(tier[w] == 2 for w in ws) - 0.7 * sum(len(w) == 2 for w in ws)

R, C, seed = int(sys.argv[1]), int(sys.argv[2]), int(sys.argv[3])
tries = int(sys.argv[4]) if len(sys.argv) > 4 else 1500
rnd = random.Random(seed); best = None
for t in range(tries):
    dens = rnd.uniform(0.14, 0.22)
    pat = [['#'] * C] + [['#'] + ['.' if rnd.random() > dens else '#' for _ in range(C - 1)] for _ in range(R - 1)]
    sl = slots(pat)
    if not ok_pattern(pat, sl): continue
    res = fill(sl, rnd, [20000])
    if not res: continue
    sc = score(sl, res[1])
    if best is None or sc > best[0]: best = (sc, pat, sl, res)
if not best: print("none"); sys.exit()
sc, pat, sl, (grid, assign) = best
for r in range(R): print(' '.join(grid.get((r, c), '#') for c in range(C)))
for i, s in enumerate(sl):
    w = assign[i]
    print(s[0], s[1], s[2], w, ["hard", "easy", "dict"][tier[w]], HARD.get(w) or EASY.get(w) or "?")
print("score", round(sc, 2))
