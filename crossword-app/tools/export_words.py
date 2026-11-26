"""One-off: copy the clue tables from the old Python modules into content/words/*.txt."""
import importlib, os
import gen_pack as g
from clue_tiers import TIERS as T1
from clue_tiers2 import TIERS2 as T2
from words_more import TIERS_MORE as T3
from clue_tiers_filler import TIERS_FILLER as T4
OUT = os.path.join(os.path.dirname(__file__), '..', 'content', 'words')

def write(name, header, tiers):
    with open(os.path.join(OUT, name), 'w', encoding='utf-8') as f:
        f.write(header)
        for w, t in tiers.items():
            f.write(f'{w} | {t[0]} | {t[1]} | {t[2]}\n')

gen = {**T4, **T3, **T2, **T1}
gen = {w: t for w, t in gen.items() if w not in g.SKIP}
write('umumi.txt', '# Ümumi sözlər: əsas pak (Başlanğıc) və tematik şəbəkələri bağlamaq üçün.\n'
      '# Format: SÖZ | çətin | orta | asan   (hər ipucu 16 hərfə qədər)\n', gen)
for mod, pid, title in [('theme_geo', 'az-cografiya', 'Coğrafiya'), ('theme_tarix', 'az-tarix', 'Tarix və dünya'),
                        ('theme_edebiyyat', 'az-edebiyyat', 'Ədəbiyyat'), ('theme_musiqi', 'az-musiqi', 'Musiqi və muğam'),
                        ('theme_kimya', 'az-kimya', 'Kimya'), ('theme_yeniil', 'az-yeniil', 'Yeni il')]:
    write(f'{pid}.txt', f'# title: {title}\n# Format: SÖZ | çətin | orta | asan\n', importlib.import_module(mod).TIERS)
