"""Dev helper: replace whole entries. stdin lines: FILE WORD | a | b | c  (or FILE -WORD to delete)."""
import sys, os, re
W = os.environ.get('W') or os.path.join(os.path.dirname(__file__), '..', 'content', 'words')
for line in sys.stdin:
    line = line.strip()
    if not line: continue
    f, rest = line.split(' ', 1)
    p = os.path.join(W, f + '.txt'); lines = open(p).read().split('\n')
    if rest.startswith('-'):
        w = rest[1:].strip(); lines = [l for l in lines if not l.startswith(w + ' |')]
    else:
        w = rest.split('|')[0].strip(); hit = False
        for i, l in enumerate(lines):
            if l.startswith(w + ' |'): lines[i] = rest; hit = True
        if not hit: lines.insert(-1 if lines[-1] == '' else len(lines), rest)
    open(p, 'w').write('\n'.join(lines))
