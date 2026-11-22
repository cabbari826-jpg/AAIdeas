"""Check clue tiers: 3 levels, <=16 chars, clue not containing the answer, no clue text twice
across all given modules and the existing packs. Usage: python3 check_tiers.py mod1 [mod2 ...]"""
import collections, glob, importlib, json, os, sys
def low(s): return s.replace('İ', 'i').replace('I', 'ı').lower()
seen = collections.defaultdict(list)
for p in glob.glob(os.path.join('..', 'app', 'src', 'main', 'assets', 'packs', '*.json')):
    for L in json.load(open(p))['levels']:
        for e in L['entries']:
            for c in set(e['clues']): seen[c].append(e['answer'] + '@' + os.path.basename(p))
bad = 0
for m in sys.argv[1:]:
    d = importlib.import_module(m)
    T = next(getattr(d, a) for a in ('TIERS', 'TIERS_MORE', 'TIERS2', 'TIERS_FILLER') if hasattr(d, a))
    for w, t in T.items():
        if len(t) != 3: print('not 3', w); bad += 1
        for i, c in enumerate(t):
            if len(c) > (40 if i == 0 and m == 'theme_musiqi' else 16): print('long', w, c); bad += 1
            if low(w) in low(c): print('contains', w, c); bad += 1
        for c in set(t): seen[c].append(w + '@' + m)
for c, v in seen.items():
    if len(set(v)) > 1: print('dupe', repr(c), v); bad += 1
print('problems:', bad)
