"""Pick base forms (lemmas) out of the expanded Azerbaijani hunspell word list.

Input: all word forms, one per line (made by expand_cspell.mjs from @cspell/dict-az-az).
Output: candidates_az.txt, upper-case crossword answers, one per line. No clues:
clues are written and reviewed by hand.

Heuristic. A word is an inflected form if it is another listed word plus a case,
plural, possessive or copula ending (kitab+da, kitab+lar+ı). Such words are dropped,
unless they take a plural themselves (bal+a = "bala", but "balalar" exists, so kept).
A word that is the first half of a hyphenated pair or takes a derivational suffix
(oyun-məşq, üzümçü, susuz) is kept anyway. Verbs are kept only as infinitives (-maq/-mək) with a listed past form.
Proper names (capitalized) and hyphenated words are dropped.
"""
import re, sys

LETTERS = "abcçdeəfgğhxıijkqlmnoöprsştuüvyz"
BACK = set("aıou")
VOWELS = set("aıoueəiöü")
# broken affix output in the source list (tıxaılma, şənbəində)
JUNK = re.compile(r"əi|aı|əı|ıı|iı|uı|üı|öı|eı|oı")
DERIV = ("çı", "çi", "çu", "çü", "lı", "li", "lu", "lü", "lıq", "lik", "luq", "lük",
         "sız", "siz", "suz", "süz", "laşmaq", "ləşmək")
BAD_END = re.compile(r"s[ıiuü]n(da|də|dan|dən|a|ə|ı|i)$|(mağı|məyi)$")
VERB_FORMS = ("", "ır", "ir", "ur", "ür", "ar", "ər", "r", "dı", "di", "du", "dü", "ıb", "ib", "ub", "üb",
              "mağı", "məyi", "mağa", "məyə", "ıl", "il", "ul", "ül", "ın", "in", "un", "ün", "an", "ən")
ENDINGS = sorted({
    "lar", "lər",
    "a", "ə", "ya", "yə", "na", "nə",
    "da", "də", "ta", "tə", "nda", "ndə",
    "dan", "dən", "tan", "tən", "ndan", "ndən",
    "ın", "in", "un", "ün", "nın", "nin", "nun", "nün",
    "ı", "i", "u", "ü", "yı", "yi", "yu", "yü", "nı", "ni", "nu", "nü",
    "sı", "si", "su", "sü",
    "ım", "im", "um", "üm", "m", "ımız", "imiz", "umuz", "ümüz", "mız", "miz", "muz", "müz",
    "ın", "ınız", "iniz", "unuz", "ünüz", "nız", "niz", "nuz", "nüz",
    "dır", "dir", "dur", "dür", "tır", "tir", "tur", "tür",
    "dakı", "dəki", "kı", "ki", "la", "lə", "yla", "ylə",
}, key=len)

def upper_az(w):
    return w.replace("i", "İ").replace("ı", "I").upper()

def plural(w):
    vs = [c for c in w if c in VOWELS]
    return w + ("lar" if vs and vs[-1] in BACK else "lər")

def is_verb_form(w, low):
    """yazır, yazdı, yazmağı, yazıl, yazan: forms of the listed verb yazmaq.
    A form that also takes a plural is a noun too (yaz/yazlar) and is kept by the caller."""
    for e in VERB_FORMS:
        if w.endswith(e):
            stem = w[: len(w) - len(e)]
            if len(stem) >= 2 and (stem + "maq" in low or stem + "mək" in low):
                return True
    return False

def main(src, dst, max_len=9):
    forms = {l.strip() for l in open(src, encoding="utf8")}
    low = {w for w in forms if w and re.fullmatch(f"[{LETTERS}]+", w)}
    hyph = {f.split("-")[0] for f in forms if "-" in f}
    keep = []
    for w in low:
        if not any(c in VOWELS for c in w):
            continue
        is_verb = re.search(r"(maq|mək)$", w)
        if not 2 <= len(w) <= max_len + (1 if is_verb else 0):
            continue
        inflected = any(w.endswith(e) and len(w) - len(e) >= 2 and w[:-len(e)] in low for e in ENDINGS)
        # a stem of hyphenated pairs or derived words is a real word (oyun-məşq, üzümçü)
        rescued = not JUNK.search(w) and (w in hyph or any(w + d in low for d in DERIV))
        if is_verb:
            # infinitive whose past tense is listed: yazmaq -> yazdı
            ok = not JUNK.search(w) and any(w[:-3] + s in low for s in ("dı", "di", "du", "dü"))
        elif re.search(r"(lar|lər)$", w) or JUNK.search(w) or BAD_END.search(w) or (is_verb_form(w, low) and plural(w) not in low):
            ok = False
        elif inflected:
            # bal+a = "bala": keep only if it takes its own plural
            ok = plural(w) in low
        else:
            # not built from another word: keep if it inflects, or is short
            ok = plural(w) in low or sum(w + e in low for e in ("da", "də", "dır", "dir", "dur", "dür")) >= 1 or len(w) <= 5
        if not ok and not (rescued and not BAD_END.search(w) and not re.search(r"(lar|lər)$", w)):
            continue
        keep.append(upper_az(w))
    keep.sort()
    open(dst, "w", encoding="utf8").write("\n".join(keep) + "\n")
    print(f"{len(forms)} forms -> {len(keep)} candidates")

if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
