"""Turn out_<PACK_ID>.json into assets/packs/<PACK_ID>.json with three clue levels per answer.

Usage: python3 build_theme.py THEME_MODULE PACK_ID "Title"   (run from tools/)
Clues come from the theme module first, then from the general clue files.
"""
import importlib, json, os, sys
from clue_tiers import TIERS as T1
from clue_tiers2 import TIERS2 as T2
from words_more import TIERS_MORE as T3
try:
    from clue_tiers_filler import TIERS_FILLER as T4
except ImportError:
    T4 = {}

mod, pack_id, title = sys.argv[1], sys.argv[2], sys.argv[3]
theme = importlib.import_module(mod).TIERS
others = {}
for m in ('theme_geo', 'theme_tarix', 'theme_edebiyyat', 'theme_musiqi'):
    others.update(importlib.import_module(m).TIERS)
tiers = {**T4, **T3, **T2, **T1, **others, **theme}
levels = json.load(open(f'out_{pack_id}.json'))
missing = sorted({e['answer'] for L in levels for e in L['entries'] if e['answer'] not in tiers})
if missing: sys.exit('no clues for: ' + ' '.join(missing))
for L in levels:
    for e in L['entries']:
        t = tiers[e['answer']]
        e.update(clue=t[2], source='claude-draft', status='candidate', clues=t)
pack = dict(formatVersion=1, packId=pack_id, language='az', normalization='az-v1', contentVersion=1,
            title=title, levels=levels)
out = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'assets', 'packs', f'{pack_id}.json')
json.dump(pack, open(out, 'w'), ensure_ascii=False, indent=1)
open(out, 'a').write('\n')
print(pack_id, len(levels), 'levels')
