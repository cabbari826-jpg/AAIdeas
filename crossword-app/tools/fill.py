import random, sys, json
import os
if os.environ.get('WORDLIST') == 'hard':
    from words_hard import WORDS_HARD
    from words import WORDS as _EASY
    # Hard list first; easy words fill the gaps (their clues are already reviewed drafts).
    WORDS = dict(_EASY); WORDS.update(WORDS_HARD)
else:
    from words import WORDS
MAXLEN = int(os.environ.get('MAXLEN', '6'))
EXCLUDE = set(filter(None, os.environ.get('EXCLUDE', '').split(',')))
W = {}
for w in WORDS:
    if w not in EXCLUDE: W.setdefault(len(w), []).append(w)

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

def valid_pattern(pat, sl):
    covered = set(p for s in sl for p in s[3])
    for r, row in enumerate(pat):
        for c, ch in enumerate(row):
            if ch == '.' and (r, c) not in covered: return False
    if any(len(s[3]) > MAXLEN for s in sl): return False
    return True

def fill(pat, sl, rng, budget=[0]):
    grid = {}; used = set(); assign = {}
    def cands(s):
        L = len(s[3]); res = []
        for w in W.get(L, []):
            if w in used: continue
            if all(grid.get(p, w[i]) == w[i] for i, p in enumerate(s[3])): res.append(w)
        return res
    def bt():
        budget[0] += 1
        if budget[0] > 200000: return False
        todo = [i for i in range(len(sl)) if i not in assign]
        if not todo: return True
        best = None
        for i in todo:
            cs = cands(sl[i])
            if best is None or len(cs) < len(best[1]): best = (i, cs)
            if not cs: return False
        i, cs = best; rng.shuffle(cs)
        for w in cs:
            prev = {p: grid.get(p) for p in sl[i][3]}
            for k, p in enumerate(sl[i][3]): grid[p] = w[k]
            assign[i] = w; used.add(w)
            if bt(): return True
            del assign[i]; used.discard(w)
            for p, v in prev.items():
                if v is None: grid.pop(p, None)
                else: grid[p] = v
        return False
    budget[0] = 0
    return (grid, assign) if bt() else None

def randpat(R, C, rng, dens):
    pat = [['#'] * C] + [['#'] + ['.' if rng.random() > dens else '#' for _ in range(C - 1)] for _ in range(R - 1)]
    return pat

def score(pat, sl):
    letters = sum(row.count('.') for row in pat)
    cross = 0
    cov = {}
    for s in sl:
        for p in s[3]: cov[p] = cov.get(p, 0) + 1
    cross = sum(1 for v in cov.values() if v == 2)
    pen2 = int(os.environ.get('PEN2', '2'))
    longb = int(os.environ.get('LONGB', '0'))
    return letters + 2 * cross - pen2 * sum(1 for s in sl if len(s[3]) == 2) + longb * sum(1 for s in sl if len(s[3]) >= 5)

R, C = int(sys.argv[1]), int(sys.argv[2]); seed = int(sys.argv[3])
rng = random.Random(seed); best = None
for t in range(int(sys.argv[4]) if len(sys.argv)>4 else 4000):
    pat = randpat(R, C, rng, float(sys.argv[5]) if len(sys.argv)>5 else 0.22)
    sl = slots(pat)
    if not valid_pattern(pat, sl): continue
    sc = score(pat, sl)
    if best and sc <= best[0]: continue
    res = fill(pat, sl, rng)
    if res: best = (sc, pat, sl, res)
if not best: print("none"); sys.exit()
sc, pat, sl, (grid, assign) = best
for r in range(R):
    print(' '.join(grid.get((r, c), '#') for c in range(C)))
out = []
for i, s in enumerate(sl):
    out.append(dict(dir=s[0], clueRow=s[1], clueCol=s[2], answer=assign[i]))
    print(s[0], s[1], s[2], assign[i], WORDS[assign[i]])
json.dump(dict(rows=R, cols=C, entries=out), open(f'out_{R}x{C}_{seed}.json', 'w'), ensure_ascii=False)
