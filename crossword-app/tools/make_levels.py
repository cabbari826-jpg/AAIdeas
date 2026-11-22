"""Make new crossword levels from the word lists in content/words/ — one command, no Claude needed.

    python make_levels.py PACK COUNT [SEED] [--out FILE.json]

PACK   pack id, e.g. az-starter (main pack), az-novruz, az-cografiya, az-mini (daily mini grids).
COUNT  how many new levels to add at the end of the pack.
SEED   any number; a different number gives different grids (default: random).
--out  write the whole pack (old + new levels) to FILE.json instead of into the app.
Exit code: 0 done, 2 fewer levels than asked (not enough words), 1 error.

Words come from content/words/<PACK>.txt (the main pack and every themed pack also use
content/words/umumi.txt to close the grid). Each line there is
    SÖZ | çətin ipucu | orta ipucu | asan ipucu
The script never reuses a word, key word or clue text already in any pack, checks every clue
(at most 16 characters, three different clues, the answer not inside), writes the pack into
app/src/main/assets/ and prints the duplicate report. The app still runs ClueFitTest at build
time (the GitHub workflow does it) to be sure every clue fits its grid cell.
"""
import glob, json, os, random, re, sys

import construct
import gen_pack as g

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..')
WORDS = os.path.join(ROOT, 'content', 'words')
PACKS = os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'packs')
DOWNLOADABLE = os.path.join(ROOT, 'content', 'downloadable')   # packs offered for download from the game
MINI = os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'mini')
MAX_CLUE = 16
MAX_WORD = 10.25   # widest single word (in letter widths) that still fits a grid cell; ClueFitTest has the last word


def word_width(t):
    """Rough printed width of a word: narrow letters count half, m/w and capitals a bit more."""
    return sum(0.55 if c in 'iljtfrıİI.,-' else 1.35 if c in 'mwMW' else 1.15 if c.isupper() else 1.0 for c in t)
ONLY_OWN_WORDS = {'az-usaq'}   # packs that never borrow words from umumi*.txt


def up(s):
    return s.replace('i', 'İ').replace('ı', 'I').upper()


def read_words(name):
    """Return (title, {WORD: [hard, medium, easy]}) from content/words/<name>.txt."""
    path = os.path.join(WORDS, name + '.txt')
    title, out, errs = None, {}, []
    if not os.path.exists(path): return title, out
    for n, line in enumerate(open(path, encoding='utf-8-sig'), 1):
        line = line.strip()
        if not line: continue
        if line.startswith('#'):
            if line[1:].strip().lower().startswith('title:'): title = line.split(':', 1)[1].strip()
            continue
        parts = [p.strip() for p in line.split('|')]
        if len(parts) != 4 or not all(parts):
            errs.append(f'{name}.txt line {n}: need "SÖZ | çətin | orta | asan", got: {line}'); continue
        w = up(parts[0]).replace(' ', '')
        bad = [c for c in parts[1:] if len(c) > MAX_CLUE and not (name == 'az-musiqi' and c is parts[1])]
        bad += [c for c in parts[1:] if any(word_width(t) > MAX_WORD for t in c.split()) and c not in bad
                and not (name == 'az-musiqi' and c is parts[1])]
        if bad: errs.append(f'{name}.txt line {n}: clue too long ({MAX_CLUE} letters at most, one word too wide for a cell): {bad[0]}')
        if any(t.startswith(w) for c in parts[1:] for t in re.split(r'[^\w]+', up(c))): errs.append(f'{name}.txt line {n}: the clue gives the answer away: {line}')
        if w in out: errs.append(f'{name}.txt line {n}: {w} is listed twice')
        out[w] = parts[1:]
    if errs: sys.exit('\n'.join(errs + [f'Fix these lines in content/words/{name}.txt and run again.']))
    return title, out


def load(path):
    return json.load(open(path, encoding='utf-8')) if os.path.exists(path) else None


def used_everywhere(skip_path):
    """Answers, key words and clue texts of every pack in assets/packs (except skip_path)."""
    words, clues = set(), set()
    for p in glob.glob(os.path.join(PACKS, '*.json')) + glob.glob(os.path.join(DOWNLOADABLE, '*.json')):
        if os.path.abspath(p) == os.path.abspath(skip_path): continue
        for L in load(p)['levels']:
            for e in L['entries']:
                words.add(e['answer']); clues.update(e.get('clues') or [e['clue']])
            if L.get('keyword'): words.add(L['keyword']['word'])
    wod = os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'word_of_day.json')
    if os.path.exists(wod):   # the word of the day must not show up in a level
        words.update(w['word'] for w in load(wod))
    return words, clues


def keyword_for(grid, pool, rng, lo=5, hi=8):
    cells = list(grid.items())
    pool = [w for w in pool if lo <= len(w) <= hi]
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


def build_theme(theme, other, rng, tries, min_entries, max_words, min_theme=0.0):
    if not theme: theme, other = other, []
    if not theme: return None
    best = None
    for _ in range(tries):
        got = construct.build(theme, other, rng, max_words=max_words)
        if not got: continue
        grid, ent = got
        letters = sum(1 for v in grid.values() if v not in (None, '#'))
        nt = sum(1 for e in ent if e[3] in theme)
        if sum(1 for e in ent if len(e[3]) == 2) > 1: continue   # at most one 2-letter word
        sc = letters + 6 * nt
        if len(ent) >= min_entries and nt >= min_theme * len(ent) and (best is None or sc > best[0]): best = (sc, grid, ent)
    if not best: return None
    _, grid, ent = best
    grid = {p: v for p, v in grid.items() if v not in (None, '#')}
    return grid, [(d, r, c, w) for d, r, c, w in ent]


def build_main(words, rng, tries):
    best = None
    for _ in range(tries):
        got = g.climb(rng)
        if not got: continue
        pat, sl = got
        sc = g.score(pat, sl)
        if best and sc <= best[0]: continue
        res = g.fill(sl, words, rng)
        if res: best = (sc, sl, res)
    if not best: return None
    _, sl, (grid, assign) = best
    return grid, [(s[0], s[1], s[2], assign[i]) for i, s in enumerate(sl)]


def main():
    args = sys.argv[1:]
    out = None
    if '--out' in args:
        i = args.index('--out'); out = args[i + 1]; del args[i:i + 2]
    if len(args) < 2:
        sys.exit(__doc__)
    pack_id, count = args[0], int(args[1])
    seed = int(args[2]) if len(args) > 2 else random.randrange(1 << 30)
    rng = random.Random(seed)
    mini = pack_id == 'az-mini'
    path = os.path.join(MINI if mini else PACKS, pack_id + '.json')
    if not mini and not os.path.exists(path) and os.path.exists(os.path.join(DOWNLOADABLE, pack_id + '.json')):
        path = os.path.join(DOWNLOADABLE, pack_id + '.json')
    pack = load(path)
    title, theme = read_words(pack_id)
    general = {}
    for f in sorted(glob.glob(os.path.join(WORDS, 'umumi*.txt'))):
        general.update(read_words(os.path.basename(f)[:-4])[1])
    if pack is None:
        if not title: sys.exit(f'new pack {pack_id}: put "# title: Name" in content/words/{pack_id}.txt')
        pack = dict(formatVersion=1, packId=pack_id, language='az', normalization='az-v1', contentVersion=1,
                    title=title, levels=[])
    if pack_id in ONLY_OWN_WORDS:
        general = {}   # kids: no grown-up filler words
    if mini:
        # The daily mini game is separate: words and clues only have to be new inside az-mini.
        taken, clues_taken = set(), set()
        pool = {}
        for f in glob.glob(os.path.join(WORDS, '*.txt')):
            pool.update(read_words(os.path.basename(f)[:-4])[1])
        pool.update(theme)
    else:
        taken, clues_taken = used_everywhere(path)
        pool = {**general, **theme}
    for L in pack['levels']:
        for e in L['entries']:
            taken.add(e['answer']); clues_taken.update(e.get('clues') or [e['clue']])
        if L.get('keyword'): taken.add(L['keyword']['word'])

    def usable(w):
        return w not in taken and not (set(pool[w]) & clues_taken)

    tries = int(os.environ.get('TRIES', '300'))
    start = len(pack['levels'])
    made = 0
    for n in range(count):
        if pack_id == 'az-starter':
            # Main pack: every word comes from umumi*.txt; the grid grows from crossings.
            construct.R, construct.C = g.R, g.C
            words = [w for w in general if usable(w) and len(w) <= g.MAXLEN]
            got = build_theme(words, [], rng, tries, 7, 16) or build_theme(words, [], rng, tries, 6, 16)
            rows, cols = g.R, g.C
        else:
            if mini:
                construct.R, construct.C = rng.choice([(5, 5), (6, 5), (5, 6), (6, 6)])
                th = [w for w in pool if usable(w) and len(w) <= max(construct.R, construct.C) - 1]
                got = build_theme(th, [], rng, tries, 5, 6)
            else:
                construct.R, construct.C = g.R, g.C
                th = [w for w in theme if usable(w) and len(w) <= g.MAXLEN]

                oth = [w for w in general if usable(w) and w not in theme and len(w) <= g.MAXLEN]
                got = build_theme(th, oth, rng, tries, 6, 16, float(os.environ.get('MIN_THEME', '0.5')))
            rows, cols = construct.R, construct.C
        if not got:
            src = 'umumi*.txt' if pack_id == 'az-starter' else f'{pack_id}.txt'
            print(f'Stopped after {n} new levels: not enough unused words in content/words/{src} '
                  f'(at least half of every level must come from it). Add words there and run again.')
            break
        grid, ent = got
        answers = [e[3] for e in ent]
        taken.update(answers)
        for w in answers: clues_taken.update(pool[w])
        kw = None if mini else (keyword_for(grid, [w for w in theme if usable(w)], rng)
                                 or keyword_for(grid, [w for w in general if usable(w)], rng))
        if kw: taken.add(kw[0])
        k = start + n
        level = dict(id=f'{pack_id}-{k + 1:03d}', rows=rows, cols=cols, difficulty=min(3, 1 + k * 3 // max(start + count, 30)),
                     entries=[dict(id=i + 1, direction=d, clueRow=r, clueCol=c, answer=w, clue=pool[w][2],
                                   source='claude-draft', status='candidate', clues=pool[w])
                              for i, (d, r, c, w) in enumerate(ent)])
        if kw: level['keyword'] = dict(word=kw[0], cells=kw[1], source='claude-draft')
        pack['levels'].append(level)
        made += 1
        nt = sum(1 for w in answers if w in (general if pack_id == 'az-starter' else theme))
        print(level['id'], f'{nt}/{len(answers)} from the word list', 'key word:', kw[0] if kw else '-', ' '.join(answers))
    dest = out or path
    os.makedirs(os.path.dirname(os.path.abspath(dest)), exist_ok=True)
    with open(dest, 'w', encoding='utf-8') as f:
        json.dump(pack, f, ensure_ascii=False, indent=1); f.write('\n')
    print(f'{pack_id}: {made} new, {len(pack["levels"])} levels in all (seed {seed}). Saved {dest}')
    sys.exit(0 if made == count else 2)


if __name__ == '__main__':
    main()
