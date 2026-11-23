"""Write app/src/main/baseline-prof.txt: a startup profile rule for every method of the app's own code.

    ./gradlew compileDebugKotlin && python tools/make_baseline_profile.py

ART then compiles our code ahead of time on install (profileinstaller does it for a sideloaded
APK, Google Play does it for store installs), so the start and the level screen don't run
interpreted on cheap phones. R8 renames the classes; AGP rewrites these rules to the new names.
Rules for methods that no longer exist are ignored, so an old file is harmless; re-run after
big changes. Needs a JDK (javap).
"""
import os, re, subprocess, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..', 'app')
CLASSES = os.path.join(ROOT, 'build', 'tmp', 'kotlin-classes', 'debug')
OUT = os.path.join(ROOT, 'src', 'main', 'baseline-prof.txt')
SKIP = ('com/aaideas/krossvord/update/',)   # prototype-only updater

HEADER = """# Startup profile for Açar Söz, made by tools/make_baseline_profile.py (do not edit by hand).
# Libraries (Compose, Material3, Haze, kotlinx.serialization) bring their own profiles; this covers our code.
"""

def main():
    files = []
    for d, _, names in os.walk(CLASSES):
        for n in names:
            if n.endswith('.class'):
                rel = os.path.relpath(os.path.join(d, n), CLASSES).replace(os.sep, '/')
                if not rel.startswith(SKIP): files.append(rel)
    if not files: sys.exit('no classes: run ./gradlew compileDebugKotlin first')
    files.sort()
    rules = []
    for i in range(0, len(files), 200):
        chunk = files[i:i + 200]
        out = subprocess.run(['javap', '-p', '-s', '-cp', CLASSES] + [f[:-6].replace('/', '.') for f in chunk],
                             capture_output=True, text=True, check=True).stdout
        cls, name = None, None
        for line in out.splitlines():
            m = re.match(r'^(?:[\w$.]+ )*(?:class|interface|enum) ([\w$.]+)', line.strip()) if not line.startswith(' ') else None
            if m and line.rstrip().endswith('{'):
                cls = m.group(1).split('<')[0].replace('.', '/')
                rules.append(f'L{cls};')
                continue
            s = line.strip()
            # Only method descriptors: a field rule ("->name:Type") is a syntax error for the profile compiler.
            if s.startswith('descriptor:') and s.split(':', 1)[1].strip().startswith('(') and cls and name and not re.search(r'[.\-]', name):
                rules.append(f'HSPL{cls};->{name}{s.split(":", 1)[1].strip()}')
                name = None
            elif s.endswith(');') or s.endswith('();') or ('(' in s and s.endswith(';')):
                head = s.split('(')[0].split()
                if not head: continue
                n = head[-1]
                name = '<init>' if n.replace('$', '.').split('.')[-1] == cls.split('/')[-1].split('$')[-1] or n == cls.replace('/', '.') else n
            elif s == 'static {};':
                name = '<clinit>'
    with open(OUT, 'w', encoding='utf-8') as f:
        f.write(HEADER + '\n'.join(rules) + '\n')
    print(f'{OUT}: {len(rules)} rules from {len(files)} classes')

main()
