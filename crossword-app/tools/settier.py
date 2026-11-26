"""settier.py FILE  -- reads 'WORD|hard|medium|easy' lines from stdin and replaces those entries."""
import re, sys
p = sys.argv[1]; s = open(p).read()
for line in sys.stdin:
    line = line.strip()
    if not line: continue
    w, *t = line.split('|'); assert len(t) == 3, line
    new = '"%s": [%s],' % (w, ', '.join('"%s"' % x for x in t))
    s, n = re.subn(r'^"%s": \[.*\],$' % re.escape(w), lambda m: new, s, flags=re.M)
    if n != 1: sys.exit('not found: ' + w)
open(p, 'w').write(s)
