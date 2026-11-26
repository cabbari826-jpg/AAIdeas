"""Check every word list in content/words/: format, clue length, one word in one list only,
no clue text used twice (across all lists and all packs). Prints problems, exit 1 if any.

    python check_words.py
"""
import collections, glob, os, sys
import make_levels as m

problems = []
used, _ = m.used_everywhere('')
where = collections.defaultdict(list)
clue_at = collections.defaultdict(list)
for f in sorted(glob.glob(os.path.join(m.WORDS, '*.txt'))):
    name = os.path.basename(f)[:-4]
    try:
        _, words = m.read_words(name)
    except SystemExit as e:
        problems.append(str(e)); continue
    for w, cl in words.items():
        if w in used: continue   # already in a level: its clues live in the pack now
        where[w].append(name)
        for c in dict.fromkeys(cl):
            clue_at[c].append(f'{name}:{w}')
# Clues already in the packs belong to their answers there.
for p in glob.glob(os.path.join(m.PACKS, '*.json')) + glob.glob(os.path.join(m.DOWNLOADABLE, '*.json')):
    for L in m.load(p)['levels']:
        for e in L['entries']:
            for c in dict.fromkeys(e.get('clues') or [e['clue']]):
                tag = f'pack:{e["answer"]}'
                if tag not in clue_at[c]: clue_at[c].append(tag)
for w, names in where.items():
    if len(names) > 1: problems.append(f'{w} is in several lists: {", ".join(names)}')
for c, tags in clue_at.items():
    owners = {t.split(':')[-1] for t in tags}
    if len(owners) > 1: problems.append(f'clue "{c}" used for: {", ".join(tags)}')
total = len(where)
print(f'{total} words in {len(glob.glob(os.path.join(m.WORDS, "*.txt")))} lists')
for p in problems: print(p)
print('OK' if not problems else f'{len(problems)} problems')
sys.exit(1 if problems else 0)
