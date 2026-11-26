import glob, json, importlib
from clue_tiers import TIERS as T1
from clue_tiers2 import TIERS2 as T2
from words_more import TIERS_MORE as T3
try:
    from clue_tiers_filler import TIERS_FILLER as T4
except ImportError: T4 = {}
from gen_pack import BANK
known = {**T1, **T2, **T3, **T4}
for m in ['theme_geo', 'theme_tarix', 'theme_edebiyyat', 'theme_musiqi']:
    known.update(importlib.import_module(m).TIERS)
need = []
for p in sorted(glob.glob('out_az-*.json')):
    for L in json.load(open(p)):
        need += [e['answer'] for e in L['entries'] if e['answer'] not in known]
print(len(need), ' '.join(f'{w}={BANK.get(w, "?")}' for w in need))
