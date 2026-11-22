"""Append the levels from out_levels.json to the pack, with clues from clue_tiers.py.

Usage: python3 apply_tiers.py [REVIEW_MD]   (run from tools/)
Fails if a word has no three clue levels, a clue is longer than 16 characters,
or any clue text is used twice in the pack. Optionally writes a review sheet.
"""
import json, sys
from clue_tiers import TIERS
from words_more import TIERS_MORE
from clue_tiers2 import TIERS2
TIERS = {**TIERS2, **TIERS_MORE, **TIERS}
from gen_pack import PACK

pack = json.load(open(PACK))
new = json.load(open('out_levels.json'))
have = {L['id'] for L in pack['levels']}
new = [L for L in new if L['id'] not in have]

missing = sorted({e['answer'] for L in new for e in L['entries'] if e['answer'] not in TIERS})
if missing: sys.exit('no clues for: ' + ' '.join(missing))

for L in new:
    for e in L['entries']:
        t = TIERS[e['answer']]
        assert len(t) == 3 and all(len(x) <= 16 for x in t), (e['answer'], t)
        e.update(clue=t[2], source='claude-draft', status='candidate', clues=t)
pack['levels'] += new

seen = {}
for L in pack['levels']:
    for e in L['entries']:
        for c in dict.fromkeys(e['clues']):
            if c in seen: sys.exit(f'clue "{c}" used for {seen[c]} and {e["answer"]}')
            seen[c] = e['answer']

json.dump(pack, open(PACK, 'w'), ensure_ascii=False, indent=1)
open(PACK, 'a').write('\n')
print('levels now:', len(pack['levels']))

if len(sys.argv) > 1:
    out = ['# Подсказки на проверку', '',
           'Три уровня: сложная (показывается сначала) / средняя (бесплатно) / лёгкая (за рекламу).', '']
    for L in pack['levels']:
        kw = L.get('keyword', {}).get('word', '-')
        out += [f'## {L["id"]} (ключевое слово: {kw})', '', '| Слово | Сложная | Средняя | Лёгкая |', '|---|---|---|---|']
        out += [f'| {e["answer"]} | ' + ' | '.join(e['clues']) + ' |' for e in L['entries']]
        out.append('')
    open(sys.argv[1], 'w').write('\n'.join(out))
