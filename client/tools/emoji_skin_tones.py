#!/usr/bin/env python3
"""Vygeneruje EmojiSkinTones.kt z Unicode emoji-test.txt.

Použití:
    curl -sSfL -o emoji-test.txt https://unicode.org/Public/emoji/latest/emoji-test.txt
    python3 client/tools/emoji_skin_tones.py emoji-test.txt

Odstíny jdou jen k emoji z katalogu (EmojiCatalog.kt) a jen do Unicode Emoji 15.0 — novější by
na starších systémech, jejichž písmo je nezná, byly prázdné čtverečky (stejná mez jako katalog).

Dva výstupy:
- EMOJI_SKIN_TONES_PACKED: pět variant se STEJNÝM odstínem pro všechny osoby,
- EMOJI_MIXED_SKIN_TONES_PACKED: u emoji se dvěma osobami (👫, 🤝, 💑…) všech 25 kombinací,
  řádek = odstín první osoby, sloupec = druhé. Varianty se stejným odstínem v ní jsou taky
  (úhlopříčka), takže nabídka je jedna mřížka.

Základ varianty se hledá podle JMÉNA, ne podle kódových bodů: smíšené varianty páru se píšou
rozloženě (👩🏻‍🤝‍👨🏼), kdežto základ je jeden znak (👫).
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PKG = ROOT / "client/shared/src/commonMain/kotlin/eu/tintera/plume/features/conversation"
MAX_VERSION = (15, 0)
TONES = ["1F3FB", "1F3FC", "1F3FD", "1F3FE", "1F3FF"]
TONE_NAME = re.compile(r"(?:light|medium-light|medium|medium-dark|dark) skin tone")
LINE = re.compile(r"^([0-9A-F ]+?)\s*;\s*fully-qualified\s*#\s*\S+\s+E(\d+)\.(\d+)\s+(.+)$")


def base_name(name: str) -> str:
    head, sep, rest = name.partition(": ")
    if not sep:
        return name
    parts = [p for p in rest.split(", ") if not TONE_NAME.fullmatch(p)]
    return head + (": " + ", ".join(parts) if parts else "")


def main(source: str) -> None:
    entries = []  # (codepoints, emoji, name)
    for line in Path(source).read_text(encoding="utf-8").splitlines():
        m = LINE.match(line)
        if not m:
            continue
        cps = m.group(1).split()
        if (int(m.group(2)), int(m.group(3))) > MAX_VERSION:
            continue
        entries.append((cps, "".join(chr(int(c, 16)) for c in cps), m.group(4)))

    catalog_text = (PKG / "EmojiCatalog.kt").read_text(encoding="utf-8")
    by_name = {name: emoji for cps, emoji, name in entries if not any(c in TONES for c in cps)}

    same = {}   # základ -> [5 variant]
    mixed = {}  # základ -> {(i, j): varianta}
    order = []
    for cps, emoji, name in entries:
        tones = [TONES.index(c) for c in cps if c in TONES]
        if not tones:
            continue
        # „kiss: person, person" jako jméno základu není — základ je „kiss" (💏).
        base = by_name.get(base_name(name)) or by_name.get(name.partition(": ")[0])
        if base is None or base not in catalog_text:
            continue
        if base not in order:
            order.append(base)
        if len(set(tones)) == 1:
            same.setdefault(base, [None] * 5)[tones[0]] = emoji
        if len(tones) == 2:
            mixed.setdefault(base, {})[(tones[0], tones[1])] = emoji

    # U párů se stejný odstín píše jedním znakem (👫🏻); úhlopříčku mřížky bere ze `same`.
    for base, grid in mixed.items():
        for i in range(5):
            grid.setdefault((i, i), same[base][i])

    same_packed = ";".join(f"{b}:{' '.join(same[b])}" for b in order if b in same and None not in same[b])
    mixed_packed = ";".join(
        f"{b}:{' '.join(mixed[b][(i, j)] for i in range(5) for j in range(5))}"
        for b in order if b in mixed and len(mixed[b]) == 25
    )
    version = re.search(r"# Version: (\S+)", Path(source).read_text(encoding="utf-8")).group(1)
    (PKG / "EmojiSkinTones.kt").write_text(TEMPLATE.format(
        version=version, same=same_packed, mixed=mixed_packed), encoding="utf-8")


TEMPLATE = '''package eu.tintera.plume.features.conversation

/*
 * VYGENEROVÁNO z Unicode `emoji-test.txt` (UTS #51, verze souboru {version}) skriptem
 * `client/tools/emoji_skin_tones.py` — neupravovat ručně.
 *
 * Pro každé emoji z katalogu ([EMOJI_GROUPS]), které má odstíny pleti, jeho pět variant od
 * nejsvětlejší (U+1F3FB) po nejtmavší (U+1F3FF), se STEJNÝM odstínem pro všechny osoby. Jen do
 * Unicode Emoji 15.0.
 *
 * Formát: `základ:v1 v2 v3 v4 v5` oddělené středníkem. Jeden řetězec místo tisíců literálů,
 * ze stejného důvodu jako katalog.
 *
 * Data: Unicode® Emoji, © Unicode, Inc., https://www.unicode.org/terms_of_use.html
 */
internal const val EMOJI_SKIN_TONES_PACKED: String =
    "{same}"

/*
 * Emoji se dvěma osobami (👫, 🤝, 💑…), kterým jde dát každé osobě jiný odstín: všech 25
 * kombinací po řádcích — řádek je odstín první osoby, sloupec druhé, oba od nejsvětlejšího.
 * Formát jako výš, jen s 25 variantami.
 */
internal const val EMOJI_MIXED_SKIN_TONES_PACKED: String =
    "{mixed}"
'''

if __name__ == "__main__":
    main(sys.argv[1])
