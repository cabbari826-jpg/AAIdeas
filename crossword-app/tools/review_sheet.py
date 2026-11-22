"""Write a Markdown sheet with every pack, level, answer and its three clues (for the owner's review)."""
import glob, json, os, sys
PACKS = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'assets', 'packs')
files = sorted(glob.glob(os.path.join(PACKS, '*.json')), key=lambda p: (not p.endswith('az-starter.json'), p))
out = ['# Подсказки на проверку', '', 'Три уровня: сложная (показывается сначала) / средняя (бесплатно) / лёгкая (за рекламу).', '']
for p in files:
    d = json.load(open(p))
    out += [f'# {d["title"]}', '']
    for i, L in enumerate(d['levels'], 1):
        kw = L.get('keyword', {}).get('word', '-')
        out += [f'## {d["title"]} {i} (ключевое слово: {kw})', '', '| Слово | Сложная | Средняя | Лёгкая |', '|---|---|---|---|']
        out += [f'| {e["answer"]} | ' + ' | '.join(e['clues']) + ' |' for e in L['entries']]
        out.append('')
open(sys.argv[1], 'w').write('\n'.join(out))
