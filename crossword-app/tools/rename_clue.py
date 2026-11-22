"""rename_clue.py: reads 'old|new' lines from stdin and replaces that exact clue text in the
clue source files (tools/*.py) and in every pack JSON. Clue texts are unique, so this is safe."""
import glob, json, os, sys
root = os.path.dirname(os.path.abspath(__file__))
pairs = [l.rstrip('\n').split('|') for l in sys.stdin if l.strip()]
for f in glob.glob(os.path.join(root, '*.py')):
    s = open(f).read(); t = s
    for a, b in pairs: t = t.replace(f'"{a}"', f'"{b}"')
    if t != s: open(f, 'w').write(t)
for f in glob.glob(os.path.join(root, '..', 'app', 'src', 'main', 'assets', 'packs', '*.json')):
    d = json.load(open(f)); changed = False
    for L in d['levels']:
        for e in L['entries']:
            for a, b in pairs:
                if a in e['clues']:
                    e['clues'] = [b if c == a else c for c in e['clues']]; changed = True
                if e['clue'] == a: e['clue'] = b
    if changed:
        json.dump(d, open(f, 'w'), ensure_ascii=False, indent=1); open(f, 'a').write('\n')
