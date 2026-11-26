"""Constructive Scandinavian grid builder: place a long word, then keep adding words that
cross what is already there, longest and theme words first. Every word sits right after its
clue cell; parallel words never touch, so every run of letters is exactly one word."""
import random

R, C = 8, 7   # row 0 and column 0 are always clue cells


def _place(grid, w, r, c, d):
    """Return (new cells, crossings) if w fits at (r, c) going d ('A' or 'D'), else None."""
    dr, dc = (0, 1) if d == 'A' else (1, 0)
    pr, pc = r - dr, c - dc                       # clue cell before the word
    if pr < 0 or pc < 0 or grid.get((pr, pc), '#') not in ('#', None) or isinstance(grid.get((pr, pc)), str) and grid.get((pr, pc)) != '#':
        return None
    er, ec = r + dr * len(w), c + dc * len(w)     # cell after the word
    if er < R and ec < C and grid.get((er, ec)) not in (None, '#'):
        return None
    if r + dr * (len(w) - 1) >= R or c + dc * (len(w) - 1) >= C or r < 1 or c < 1:
        return None
    cells, cross = [], 0
    for i, ch in enumerate(w):
        p = (r + dr * i, c + dc * i)
        cur = grid.get(p)
        if cur == ch: cross += 1; continue
        if cur is not None: return None           # '#' or another letter
        # A new letter must not touch letters sideways.
        for q in ((p[0] + dc, p[1] + dr), (p[0] - dc, p[1] - dr)):
            if grid.get(q) not in (None, '#'): return None
        cells.append((p, ch))
    return cells, cross, (pr, pc), (er, ec)


def build(theme, other, rng, max_words=16):
    """One random construction. Returns (grid, entries) with entries [(d, clueRow, clueCol, word)]."""
    grid, used, entries = {}, set(), []
    for c in range(C): grid[(0, c)] = '#'
    for r in range(R): grid[(r, 0)] = '#'
    first = rng.choice([w for w in theme if 5 <= len(w) <= 6] or theme)
    d = rng.choice('AD')
    r, c = (rng.randrange(1, R), 1) if d == 'A' else (1, rng.randrange(1, C))
    if d == 'A' and len(first) > C - 1 or d == 'D' and len(first) > R - 1: d = 'D'; r, c = 1, rng.randrange(1, C)

    def commit(w, r, c, d, res):
        cells, _, clue, after = res
        for p, ch in cells: grid[p] = ch
        grid[clue] = '#'
        if after[0] < R and after[1] < C: grid[after] = '#'
        used.add(w); entries.append(('ACROSS' if d == 'A' else 'DOWN', clue[0], clue[1], w))

    res = _place(grid, first, r, c, d)
    if not res: return None
    commit(first, r, c, d, res)
    pool_t = [w for w in theme if w not in used]; pool_o = list(other)
    while len(entries) < max_words:
        letters = [(p, ch) for p, ch in grid.items() if ch != '#']
        best = None
        for pool, bonus in ((pool_t, 3), (pool_o, 0)):
            cand = rng.sample(pool, min(len(pool), 400))
            for w in cand:
                if w in used: continue
                for (pr_, pc_), ch in letters:
                    for i, wc in enumerate(w):
                        if wc != ch: continue
                        for d in 'AD':
                            r = pr_ - (i if d == 'D' else 0); c = pc_ - (i if d == 'A' else 0)
                            res = _place(grid, w, r, c, d)
                            if not res or res[1] == 0: continue
                            sc = len(w) + 2 * res[1] + bonus + rng.random()
                            if best is None or sc > best[0]: best = (sc, w, r, c, d, res)
            if best and pool is pool_t and best[0] >= 7: break   # good theme placement: take it
        if not best: break
        _, w, r, c, d, res = best
        commit(w, r, c, d, res)
    return grid, entries
