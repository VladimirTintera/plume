#!/usr/bin/env python3
"""
Poznámky k vydání pro uživatele, česky a anglicky.

Každý PR přidá do `release-notes/` jeden soubor (název podle větve, `/` → `-`):

    type: feat
    cs: Hledání v seznamu chatů
    en: Search in the chat list

`type` je `feat` (novinka), `fix` (oprava) nebo `internal` (uživatel nic nepozná — dokumentace,
CI, refaktoring). U `internal` se `cs`/`en` nepíšou.

Release bere soubory PŘIDANÉ mezi předchozím a aktuálním tagem a čte je z tagu, takže jsou
poznámky z tagu reprodukovatelné stejně jako verze.

    release_notes.py check <soubor>...                   – ověří formát a délku (PR check)
    release_notes.py play <prev-tag|""> <tag> <adresář>  – whatsnew-<locale> pro Google Play (PLAY_LANGS)
    release_notes.py markdown <prev-tag|""> <tag>        – sekce do popisu GitHub Release
"""

import os
import subprocess
import sys
from pathlib import Path

NOTES_DIR = "release-notes"
TYPES = ("feat", "fix", "internal")
MAX_NOTE = 100    # jedna věta; 4–5 poznámek se tak vejde do limitu Play
PLAY_LIMIT = 500  # limit Play na text „Co je nového" v jednom jazyce

LOCALES = {
    # jazyk souboru → (locale v Play, zbytek, když se nevejde, text bez uživatelských změn)
    "cs": ("cs-CZ", "• …a další drobné úpravy", "Drobné opravy a vylepšení."),
    "en": ("en-US", "• …and more improvements", "Minor fixes and improvements."),
}

# Jazyky, které jdou do Play. Poznámka pro jazyk, který nemá aplikace v záznamu v obchodě,
# by mohla shodit celý upload — nový jazyk sem patří až po nahrání záznamu v něm (`play/listing/`).
PLAY_LANGS = ("cs", "en")


def parse(text, name):
    fields = {}
    for number, line in enumerate(text.splitlines(), 1):
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        key, sep, value = line.partition(":")
        key, value = key.strip(), value.strip()
        if not sep or key not in ("type", "cs", "en"):
            raise ValueError(f"{name}:{number}: čekám „type:“, „cs:“ nebo „en:“, je tu „{line}“")
        if key in fields:
            raise ValueError(f"{name}:{number}: „{key}“ je tu podruhé")
        fields[key] = value

    kind = fields.get("type")
    if kind not in TYPES:
        raise ValueError(f"{name}: type musí být jedno z {', '.join(TYPES)}, je „{kind}“")
    if kind == "internal":
        if "cs" in fields or "en" in fields:
            raise ValueError(f"{name}: type internal nemá text — uživatel nic nepozná")
        return fields
    for lang in LOCALES:
        value = fields.get(lang, "")
        if not value:
            raise ValueError(f"{name}: chybí „{lang}:“")
        if len(value) > MAX_NOTE:
            raise ValueError(f"{name}: {lang} má {len(value)} znaků, nejvýš {MAX_NOTE}")
    return fields


def git(*args):
    return subprocess.run(["git", *args], check=True, capture_output=True, text=True).stdout


def notes_between(prev, tag):
    """Poznámky přidané v rozsahu, v pořadí přidání; novinky před opravami."""
    if prev:
        log = git("log", "--reverse", "--diff-filter=A", "--format=", "--name-only",
                  f"{prev}..{tag}", "--", NOTES_DIR)
    else:
        log = git("ls-tree", "-r", "--name-only", tag, "--", NOTES_DIR)
    paths = list(dict.fromkeys(p for p in log.splitlines() if p.endswith(".md")))

    notes = []
    for path in paths:
        try:
            content = git("show", f"{tag}:{path}")
        except subprocess.CalledProcessError:
            continue  # přidaný a v rozsahu zase smazaný
        fields = parse(content, path)
        if fields["type"] != "internal":
            notes.append(fields)
    return sorted(notes, key=lambda n: TYPES.index(n["type"]))


def play_text(notes, lang):
    _, rest, empty = LOCALES[lang]
    if not notes:
        return empty
    lines = [f"• {n[lang]}" for n in notes]
    if len("\n".join(lines)) <= PLAY_LIMIT:
        return "\n".join(lines)
    # Nevejde se: co nejvíc poznámek (novinky jsou vpředu) a místo zbytku jeden řádek.
    kept = []
    for line in lines:
        if len("\n".join(kept + [line, rest])) > PLAY_LIMIT:
            break
        kept.append(line)
    return "\n".join(kept + [rest])


def main(argv):
    command = argv[1] if len(argv) > 1 else ""
    if command == "check" and len(argv) > 2:
        failed = False
        for path in argv[2:]:
            try:
                parse(Path(path).read_text(encoding="utf-8"), path)
                print(f"ok: {path}")
            except ValueError as error:
                # Na CI jako anotace u souboru, jinak prostě chyba — pre-push hook tím
                # vypisuje čitelnou větu, ne značkování GitHubu.
                if os.environ.get("GITHUB_ACTIONS"):
                    print(f"::error file={path}::{error}")
                else:
                    print(f"chyba: {error}", file=sys.stderr)
                failed = True
        return 1 if failed else 0

    if command == "play" and len(argv) == 5:
        notes = notes_between(argv[2], argv[3])
        out = Path(argv[4])
        out.mkdir(parents=True, exist_ok=True)
        for lang in PLAY_LANGS:
            locale = LOCALES[lang][0]
            text = play_text(notes, lang)
            (out / f"whatsnew-{locale}").write_text(text, encoding="utf-8")
            print(f"--- whatsnew-{locale} ({len(text)}/{PLAY_LIMIT}) ---\n{text}")
        return 0

    if command == "markdown" and len(argv) == 4:
        notes = notes_between(argv[2], argv[3])
        # Do GitHub Release jde celý seznam, bez ořezu na limit Play.
        for lang, title in (("cs", "Co je nového"), ("en", "What's new")):
            print(f"### {title}\n")
            print("\n".join(f"- {n[lang]}" for n in notes) or f"_{LOCALES[lang][2]}_")
            print()
        return 0

    print(__doc__, file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv))
