"""Duplicate report over every pack: repeated answers, clue texts and key words,
inside a level, across levels and across packs. Writes Markdown and exits 1 if anything repeats.

Usage: python3 report_dupes.py [OUT.md]   (run from tools/)
"""
import collections, glob, json, os, sys

PACKS = sorted(glob.glob(os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'assets', 'packs', '*.json')))
answers, clues, keywords = (collections.defaultdict(list) for _ in range(3))
total_levels = total_words = 0
for p in PACKS:
    d = json.load(open(p))
    for i, L in enumerate(d['levels'], 1):
        total_levels += 1
        where = f"{d['title']} {i}"
        for e in L['entries']:
            total_words += 1
            answers[e['answer']].append(where)
            for c in dict.fromkeys(e.get('clues') or [e['clue']]):
                clues[c].append(f"{where} ({e['answer']})")
        if L.get('keyword'):
            keywords[L['keyword']['word']].append(where)

rep = ['# Отчёт о повторах', '',
       f'Паков: {len(PACKS)}, уровней: {total_levels}, слов: {total_words}.', '']
bad = 0
def section(title, d, extra=None):
    global bad
    rows = [(k, v) for k, v in sorted(d.items()) if len(v) > 1]
    if extra: rows += extra
    rep.append(f'## {title}: {len(rows)}')
    rep.append('')
    rep.extend(f'- **{k}**: ' + '; '.join(v) for k, v in rows)
    if not rows: rep.append('Повторов нет.')
    rep.append('')
    bad += len(rows)

section('Повторы ответов', answers)
section('Повторы текста подсказок', clues)
kw_clash = [(k, v + ['также ответ: ' + ', '.join(answers[k])]) for k, v in keywords.items() if k in answers]
section('Повторы ключевых слов', keywords, kw_clash)
text = '\n'.join(rep)
if len(sys.argv) > 1: open(sys.argv[1], 'w').write(text)
print(text if bad else f'OK: {total_levels} levels, {total_words} words, no repeats')
sys.exit(1 if bad else 0)
