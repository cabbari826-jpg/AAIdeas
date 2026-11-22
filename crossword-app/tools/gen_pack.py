"""Generate new levels for the pack without repeating any answer or key word.

Usage: python3 gen_pack.py COUNT [SEED]   (run from tools/)
Reads the current pack, takes the word bank from words.py + words_hard.py, skips every
answer and key word already in the pack, prefers long words (few 2- and 3-letter ones),
and writes out_levels.json with the new levels (clues still empty: fill them in
clue_tiers.py, then run apply_tiers.py).
"""
import json, os, random, sys
from words import WORDS
from words_hard import WORDS_HARD
from words_more import TIERS_MORE

PACK = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'assets', 'packs', 'az-starter.json')
R, C = 8, 7
MAXLEN = 7
MAX_TWO = int(os.environ.get('MAX_TWO', '1'))      # 2-letter words per level
MAX_THREE = int(os.environ.get('MAX_THREE', '3'))  # 3-letter words per level

BANK = dict(WORDS); BANK.update(WORDS_HARD); BANK.update({w: t[2] for w, t in TIERS_MORE.items()})
# Weak words, near-duplicates of used ones, and names kept for the themed packs.
DEFAULT_SKIP = ('İNCİR,ÇƏN,SƏN,OXU,ORAN,ANT,QAB,SAN,NUR,YENİ,ZAMAN,İY,ARX,TAN,ŞER,ARAZ,KÜR,XƏZƏR,ŞAMAXI,'
                'LƏNKƏRAN,GÖYGÖL,NİL,VOLQA,İRAN,QOBUSTAN,MUĞAM,DASTAN,ROMAN,ANAR,ŞƏHƏR,DƏNİZ,ZƏNG,DOST')
SKIP = set(filter(None, os.environ.get('SKIP', DEFAULT_SKIP).split(',')))


def slots(pat):
    out = []
    for r in range(R):
        c = 0
        while c < C:
            if pat[r][c] == '.':
                s = c
                while c < C and pat[r][c] == '.': c += 1
                if c - s >= 2: out.append(('ACROSS', r, s - 1, [(r, x) for x in range(s, c)]))
            else: c += 1
    for c in range(C):
        r = 0
        while r < R:
            if pat[r][c] == '.':
                s = r
                while r < R and pat[r][c] == '.': r += 1
                if r - s >= 2: out.append(('DOWN', s - 1, c, [(y, c) for y in range(s, r)]))
            else: r += 1
    return out


def ok_pattern(pat, sl):
    covered = {p for s in sl for p in s[3]}
    if any(pat[r][c] == '.' and (r, c) not in covered for r in range(R) for c in range(C)): return False
    lens = [len(s[3]) for s in sl]
    if max(lens) > MAXLEN: return False
    if lens.count(2) > MAX_TWO or lens.count(3) > MAX_THREE: return False
    return True


def score(pat, sl):
    cov = {}
    for s in sl:
        for p in s[3]: cov[p] = cov.get(p, 0) + 1
    letters = len(cov)
    cross = sum(1 for v in cov.values() if v == 2)
    long_ = sum(len(s[3]) for s in sl if len(s[3]) >= 5)
    return letters + 2 * cross + long_


def fill(sl, words, rng, limit=60000):
    by_len = {}
    for w in words: by_len.setdefault(len(w), []).append(w)
    grid, used, assign = {}, set(), {}
    steps = [0]

    def cands(s):
        return [w for w in by_len.get(len(s[3]), []) if w not in used
                and all(grid.get(p, w[i]) == w[i] for i, p in enumerate(s[3]))]

    def bt():
        steps[0] += 1
        if steps[0] > limit: return False
        todo = [i for i in range(len(sl)) if i not in assign]
        if not todo: return True
        best = None
        for i in todo:
            cs = cands(sl[i])
            if not cs: return False
            if best is None or len(cs) < len(best[1]): best = (i, cs)
        i, cs = best
        rng.shuffle(cs)
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

    return (grid, assign) if bt() else None


def randpat(rng, dens):
    return [['#'] * C] + [['#'] + ['.' if rng.random() > dens else '#' for _ in range(C - 1)] for _ in range(R - 1)]


def badness(pat, sl):
    covered = {p for s in sl for p in s[3]}
    unc = sum(1 for r in range(R) for c in range(C) if pat[r][c] == '.' and (r, c) not in covered)
    lens = [len(s[3]) for s in sl]
    return (unc + max(0, lens.count(2) - MAX_TWO) + max(0, lens.count(3) - MAX_THREE)
            + sum(1 for x in lens if x > MAXLEN))


def climb(rng):
    """Mutate a random pattern until it meets the length rules (keeping its density)."""
    pat = randpat(rng, rng.uniform(0.2, 0.3))
    cur = badness(pat, slots(pat))
    for _ in range(300):
        if cur == 0: break
        r, c = rng.randrange(1, R), rng.randrange(1, C)
        pat[r][c] = '#' if pat[r][c] == '.' else '.'
        b = badness(pat, slots(pat))
        if b <= cur: cur = b
        else: pat[r][c] = '#' if pat[r][c] == '.' else '.'
    sl = slots(pat)
    return (pat, sl) if cur == 0 and sl and ok_pattern(pat, sl) else None


def keyword_for(grid, taken, rng):
    """A bank word (5-7 letters) not used anywhere, spelled by distinct grid cells."""
    cells = list(grid.items())
    pool = [w for w in BANK if 5 <= len(w) <= 7 and w not in taken]
    rng.shuffle(pool)
    for w in pool:
        free = cells[:]; rng.shuffle(free); pick = []
        for ch in w:
            hit = next((x for x in free if x[1] == ch), None)
            if hit is None: break
            free.remove(hit); pick.append([hit[0][0], hit[0][1]])
        else:
            return w, pick
    return None


def main():
    count = int(sys.argv[1]); seed = int(sys.argv[2]) if len(sys.argv) > 2 else 1
    rng = random.Random(seed)
    pack = json.load(open(PACK))
    taken = set(SKIP)
    for L in pack['levels']:
        taken |= {e['answer'] for e in L['entries']}
        if L.get('keyword'): taken.add(L['keyword']['word'])
    start = len(pack['levels'])
    levels = []
    for n in range(count):
        words = [w for w in BANK if w not in taken]
        best = None
        for _ in range(int(os.environ.get('TRIES', '300'))):
            got = climb(rng)
            if not got: continue
            pat, sl = got
            sc = score(pat, sl)
            if best and sc <= best[0]: continue
            res = fill(sl, words, rng)
            if res: best = (sc, sl, res)
        if not best: sys.exit(f'level {start + n + 1}: no fill found')
        _, sl, (grid, assign) = best
        answers = [assign[i] for i in range(len(sl))]
        taken |= set(answers)
        kw = keyword_for(grid, taken, rng)
        if kw: taken.add(kw[0])
        lid = f"az-starter-{start + n + 1:03d}"
        level = dict(id=lid, rows=R, cols=C, difficulty=1 + (start + n) * 3 // (start + count),
                     entries=[dict(id=i + 1, direction=s[0], clueRow=s[1], clueCol=s[2], answer=assign[i])
                              for i, s in enumerate(sl)])
        if kw: level['keyword'] = dict(word=kw[0], cells=kw[1], source='claude-draft')
        levels.append(level)
        print(lid, kw[0] if kw else '-', ' '.join(answers), file=sys.stderr)
        for r in range(R): print('  ' + ' '.join(grid.get((r, c), '#') for c in range(C)), file=sys.stderr)
    json.dump(levels, open('out_levels.json', 'w'), ensure_ascii=False, indent=1)


if __name__ == '__main__':
    main()
