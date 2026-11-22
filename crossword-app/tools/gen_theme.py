"""Generate a themed pack: theme words first, general words only to close the grid.

Usage: python3 gen_theme.py THEME_MODULE PACK_ID "Title" COUNT [SEED]   (run from tools/)
THEME_MODULE (e.g. theme_geo) defines TIERS = {answer: [hard, medium, easy]}.
No answer or key word may repeat anything in any pack under assets/packs/.
Writes out_<PACK_ID>.json; build_theme.py then adds clues and writes the pack.
"""
import glob, importlib, json, os, random, sys
import gen_pack as g
import construct

PACKS = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'assets', 'packs')
MIN_THEME_SHARE = float(os.environ.get('MIN_THEME', '0.5'))
MIN_LETTERS = int(os.environ.get('MIN_LETTERS', '30'))


def taken_words(skip_pack):
    taken = set(g.SKIP)
    for p in glob.glob(os.path.join(PACKS, '*.json')):
        d = json.load(open(p))
        if d['packId'] == skip_pack: continue
        for L in d['levels']:
            taken |= {e['answer'] for e in L['entries']}
            if L.get('keyword'): taken.add(L['keyword']['word'])
    for p in glob.glob('out_az-*.json'):   # other themes generated but not yet built
        if p == f'out_{skip_pack}.json': continue
        d = json.load(open(p))
        for L in d:
            taken |= {e['answer'] for e in L['entries']}
            if L.get('keyword'): taken.add(L['keyword']['word'])
    return taken


def fill(sl, theme, other, rng, limit=60000):
    """Backtracking fill that tries theme words before general ones."""
    by_len = {}
    for w in theme + other: by_len.setdefault(len(w), []).append(w)
    tset = set(theme)
    grid, used, assign = {}, set(), {}
    steps = [0]

    def cands(s):
        c = [w for w in by_len.get(len(s[3]), []) if w not in used
             and all(grid.get(p, w[i]) == w[i] for i, p in enumerate(s[3]))]
        t = [w for w in c if w in tset]; o = [w for w in c if w not in tset]
        rng.shuffle(t); rng.shuffle(o)
        return t + o

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


def keyword_for(grid, pool, rng):
    cells = list(grid.items())
    pool = [w for w in pool if 5 <= len(w) <= 8]
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
    mod, pack_id, title, count = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4])
    seed = int(sys.argv[5]) if len(sys.argv) > 5 else 1
    rng = random.Random(seed)
    tiers = importlib.import_module(mod).TIERS
    taken = taken_words(pack_id) - (set(tiers) & g.SKIP)
    clash = sorted(w for w in tiers if w in taken)
    if clash: print('theme words already used elsewhere (skipped):', ' '.join(clash), file=sys.stderr)
    levels = []
    for n in range(count):
        theme = [w for w in tiers if w not in taken and len(w) <= g.MAXLEN]
        other = [w for w in g.BANK if w not in taken and w not in tiers]
        best = None
        for _ in range(int(os.environ.get('TRIES', '300'))):
            got = construct.build(theme, other, rng)
            if not got: continue
            grid, ent = got
            letters = sum(1 for v in grid.values() if v not in (None, '#'))
            nt = sum(1 for e in ent if e[3] in tiers)
            sc = letters + 3 * nt
            if len(ent) >= 6 and (best is None or sc > best[0]): best = (sc, grid, ent)
        if not best: sys.exit(f'{pack_id} level {n + 1}: no grid built')
        _, grid, ent = best
        grid = {p: v for p, v in grid.items() if v not in (None, '#')}
        sl = [(d, r, c, None) for d, r, c, w in ent]
        assign = {i: e[3] for i, e in enumerate(ent)}
        answers = [assign[i] for i in range(len(sl))]
        taken |= set(answers)
        kw = keyword_for(grid, [w for w in tiers if w not in taken], rng) or \
            keyword_for(grid, [w for w in g.BANK if w not in taken], rng)
        if kw: taken.add(kw[0])
        level = dict(id=f'{pack_id}-{n + 1:03d}', rows=g.R, cols=g.C, difficulty=1 + n * 3 // count,
                     entries=[dict(id=i + 1, direction=s[0], clueRow=s[1], clueCol=s[2], answer=assign[i])
                              for i, s in enumerate(sl)])
        if kw: level['keyword'] = dict(word=kw[0], cells=kw[1], source='claude-draft')
        levels.append(level)
        nt = sum(1 for w in answers if w in tiers)
        print(level['id'], f'theme {nt}/{len(answers)}', 'kw', kw[0] if kw else '-', ' '.join(answers), file=sys.stderr)
        json.dump(levels, open(f'out_{pack_id}.json', 'w'), ensure_ascii=False, indent=1)


if __name__ == '__main__':
    main()
