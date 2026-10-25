#!/usr/bin/env python3
"""
Poznámky k vydání pro uživatele, česky a anglicky.

Každý PR přidá do `release-notes/` jeden soubor (název podle větve, `/` → `-`):

    type: feat
    cs: Hledání v seznamu chatů
    en: Search in the chat list

`type` je `feat` (novinka), `fix` (oprava) nebo `internal` (uživatel nic nepozná — dokumentace,
CI, refaktoring). U `internal` se `cs`/`en` nepíšou.

Volitelné `units:` říká, které platformy změnu vydají (`android`, `ios`, `desktop`, `wear`,
zkratky `all`/`client` = android, ios a desktop; víc oddělit čárkou nebo mezerou):

    type: fix
    units: android
    cs: Sdílení obrazovky v hovoru
    en: Screen sharing in calls

Bez `units:` platí poznámka pro android, ios a desktop — tak se psaly všechny poznámky před
zavedením pole a tak zůstávají. Hodinky (`wear`) se musí jmenovat výslovně: jejich aplikace umí
zlomek telefonu, takže by do „Co je nového" na hodinkách nepatřila skoro žádná stará poznámka
a ani nová, která o hodinkách mlčí.

Release bere soubory PŘIDANÉ mezi předchozím a aktuálním tagem a čte je z tagu, takže jsou
poznámky z tagu reprodukovatelné stejně jako verze.

    release_notes.py check <soubor>...                   – ověří formát a délku (PR check)
    release_notes.py exempt <změněný soubor>...          – 0, když PR mění jen komunitní překlady (bez poznámky)
    release_notes.py play <prev-tag|""> <tag> <adresář> [android|wear]
                                                         – whatsnew-<locale> pro Google Play (PLAY_LANGS)
    release_notes.py markdown <prev-tag|""> <tag> [kanál] – sekce do popisu GitHub Release (kanál i `wear`)
    release_notes.py bundle <soubor.kt>                  – historie vydání do aplikace (Co je nového)
"""

import os
import re
import subprocess
import sys
from pathlib import Path

NOTES_DIR = "release-notes"
TYPES = ("feat", "fix", "internal")
KEYS = ("type", "units", "cs", "en")

# Platformy, které změnu vydávají. `android` a `desktop` jsou kanály vydání (`CHANNELS` níž),
# `ios` se zatím nevydává — poznámka jen pro iOS proto dnes nejde nikam. `wear` vychází s tagem
# Androidu (vlastní track v Play), vlastní kanál nemá.
UNITS = ("android", "ios", "desktop", "wear")
# Plnohodnotné klienty: poznámka bez `units:` a zkratky. Hodinky v nich nejsou, viz docstring.
CLIENT_UNITS = ("android", "ios", "desktop")
UNIT_ALIASES = {"all": CLIENT_UNITS, "client": CLIENT_UNITS}
# Platformy, které jdou do Play (`play`), a track pro každou — vychází se do obou z téhož tagu.
PLAY_UNITS = ("android", "wear")
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

# Jazyky, které píšeme sami: změna v nich je změna aplikace a poznámku potřebuje. Zrcadlí
# výchozí angličtinu a `completeLocales` v client/resources/build.gradle.kts — nový jazyk psaný
# námi patří do obou míst.
OWN_LOCALES = ("en", "cs")

# Překlady, které posílá Weblate: texty aplikace (Compose resources) a iOS (Localizable.strings).
TRANSLATION_FILES = (
    re.compile(r"^client/resources/src/commonMain/composeResources/values-(?P<locale>[A-Za-z]+)(?:-r?[A-Za-z]+)?/strings\.xml$"),
    re.compile(r"^iosApp/(?:iosApp|ShareExtension)/(?P<locale>[A-Za-z]+)(?:-[A-Za-z]+)?\.lproj/Localizable\.strings$"),
)


def community_translation(path):
    """Je soubor překlad jazyka, který nepíšeme sami (Weblate)?"""
    for pattern in TRANSLATION_FILES:
        match = pattern.match(path)
        if match:
            return match.group("locale") not in OWN_LOCALES
    return False


def parse_units(value, name):
    units = []
    for token in value.replace(",", " ").split():
        for unit in UNIT_ALIASES.get(token, (token,)):
            if unit not in UNITS:
                raise ValueError(
                    f"{name}: neznámá platforma „{token}“ v „units:“ — čekám "
                    f"{', '.join(UNITS)} nebo {', '.join(UNIT_ALIASES)}"
                )
            if unit not in units:
                units.append(unit)
    if not units:
        raise ValueError(f"{name}: „units:“ je prázdné — bez řádku platí poznámka pro všechny")
    return tuple(units)


def parse(text, name):
    fields = {}
    for number, line in enumerate(text.splitlines(), 1):
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        key, sep, value = line.partition(":")
        key, value = key.strip(), value.strip()
        if not sep or key not in KEYS:
            raise ValueError(f"{name}:{number}: čekám „{'“, „'.join(KEYS)}“, je tu „{line}“")
        if key in fields:
            raise ValueError(f"{name}:{number}: „{key}“ je tu podruhé")
        fields[key] = value

    kind = fields.get("type")
    if kind not in TYPES:
        raise ValueError(f"{name}: type musí být jedno z {', '.join(TYPES)}, je „{kind}“")
    if kind == "internal":
        extra = [key for key in ("units", "cs", "en") if key in fields]
        if extra:
            raise ValueError(
                f"{name}: type internal nemá „{'“, „'.join(extra)}“ — uživatel nic nepozná"
            )
        fields["units"] = ()
        return fields

    # Bez pole = všechny klienty: přes 600 poznámek vzniklo dřív než pole a musí platit dál.
    fields["units"] = parse_units(fields["units"], name) if "units" in fields else CLIENT_UNITS
    for lang in LOCALES:
        value = fields.get(lang, "")
        if not value:
            raise ValueError(f"{name}: chybí „{lang}:“")
        if len(value) > MAX_NOTE:
            raise ValueError(f"{name}: {lang} má {len(value)} znaků, nejvýš {MAX_NOTE}")
    return fields


class GitError(subprocess.CalledProcessError):
    """Chyba gitu i s tím, co git napsal na stderr. Holé CalledProcessError hlásí jen kód
    (128 = fatální chyba) a důvod — „dubious ownership", mělký klon — zahodí."""

    def __str__(self):
        return f"{super().__str__()} {(self.stderr or '').strip()}".rstrip()


def git(*args):
    # Explicitně UTF-8: `text=True` by dekódoval kódováním systému a české znaky v poznámkách
    # by se na stroji s jiným výchozím kódováním rozsypaly.
    result = subprocess.run(["git", *args], capture_output=True, encoding="utf-8")
    if result.returncode != 0:
        raise GitError(result.returncode, result.args, result.stdout, result.stderr)
    return result.stdout


def notes_between(prev, tag, unit=None):
    """Poznámky přidané v rozsahu, v pořadí přidání; novinky před opravami.

    S `unit` jen ty, které ta platforma vydává (poznámka bez `units:` platí pro všechny klienty
    kromě hodinek).
    """
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
        if fields["type"] == "internal":
            continue
        if unit and unit not in fields["units"]:
            continue
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


# Kanály vydání = předpony tagů. Staví se z téhož kódu, ale vydávají se zvlášť a v jiném rytmu,
# takže každý má v aplikaci vlastní „Co je nového" (desktop ukazuje vydání desktopu, ne Androidu).
# Hodnota je zbytek tagu za předponou: (verze, číslo buildu nebo nic).
CHANNELS = {
    # android-<versionName>-<versionCode>, u ručně vydané verze ještě `-manual`.
    "android": re.compile(r"^(\d+(?:\.\d+)*)-(\d+)(?:-.+)?$"),
    # desktop-X.Y.Z, bez čísla buildu (MSI pozná aktualizaci jen podle verze).
    "desktop": re.compile(r"^(\d+(?:\.\d+)*)()$"),
}
# Kanál bez jednotky by v `bundle` nedostal žádnou poznámku a nic by to nenahlásilo.
assert set(CHANNELS) <= set(UNITS), "každý kanál vydání musí být i v UNITS"


def release_tags(channel):
    """Tagy vydání kanálu dosažitelné z HEAD, od nejstaršího.

    Jen `--merged HEAD`: build ze starší větve nesmí slibovat verze, které v něm nejsou. Release
    se staví přímo z tagu, takže aktuální vydání v seznamu je i se svými poznámkami.
    """
    prefix = f"{channel}-"
    tags = []
    for tag in git("tag", "--merged", "HEAD", "-l", f"{prefix}*").splitlines():
        match = CHANNELS[channel].match(tag[len(prefix):])
        if match:
            version, build = match.groups()
            tags.append((tag, version, [int(part) for part in version.split(".")] + [int(build or 0)]))
    # Podle verze, ne abecedně (0.3.10 je po 0.3.9) — stejně jako `--sort=v:refname` v release.
    return [(tag, version) for tag, version, _ in sorted(tags, key=lambda t: t[2])]


def kotlin_string(value):
    escaped = value.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$")
    return f'"{escaped}"'


def release_entries(channel):
    """Vydání kanálu jako Kotlin `ChangelogRelease(...)`, od nejnovějšího."""
    tags = release_tags(channel)
    releases = []
    for index, (tag, version) in enumerate(tags):
        # První vydání kanálu nemá s čím porovnat. Rozsah „od začátku" by u desktopu, který přišel
        # až po stovkách PR, vysypal celou historii projektu (418 poznámek u desktop-0.3.0) — ta
        # patří vydáním Androidu, ne do první verze pro počítač.
        if not index:
            continue
        # Vydání bez poznámek pro platformu zůstává (aplikace k němu napíše „Drobné opravy
        # a vylepšení", jako Play): kdyby chybělo, seznam by na nainstalované verzi končil o pár
        # vydání níž a vypadal by rozbitý. Od `units: wear` se to stává běžně — tag Androidu
        # vychází i kvůli hodinkám, desktopu nebo iOS. Prázdná vydání PŘED prvním s poznámkou
        # ale ne: to jsou verze z doby před `release-notes/` (Android 0.2.x), ne drobné opravy.
        notes = notes_between(tags[index - 1][0], tag, channel)
        if not notes and not releases:
            continue
        seconds = git("log", "-1", "--format=%ct", tag).strip()
        items = ",\n".join(
            "                ChangelogNote(ChangelogNoteType.{}, cs = {}, en = {})".format(
                note["type"].upper(), kotlin_string(note["cs"]), kotlin_string(note["en"])
            )
            for note in notes
        )
        notes_kotlin = f"listOf(\n{items},\n            )" if notes else "emptyList()"
        releases.append(
            "        ChangelogRelease(\n"
            f"            version = {kotlin_string(version)},\n"
            f"            date = {seconds},\n"
            f"            notes = {notes_kotlin},\n"
            "        ),"
        )
    return list(reversed(releases))  # nejnovější vydání první


def bundle(out_path):
    """Kotlin se seznamem vydání každého kanálu od nejnovějšího — „Co je nového" v Nastavení.

    Kotlin, ne resource: hodnota je dostupná synchronně na všech platformách a chyba v generátoru
    spadne při překladu, ne za běhu. Vydání bez poznámek pro uživatele (jen `internal` nebo jiné
    platformy) zůstává s prázdným seznamem.
    Do sestavy jdou všechny kanály naráz — `commonMain` je jeden, kanál si vybere platforma
    (`changelogChannel`).
    """
    lines = []
    counts = []
    for channel in CHANNELS:
        releases = release_entries(channel)
        counts.append(f"{channel} {len(releases)}")
        lines.append(f"    {kotlin_string(channel)} to listOf(")
        lines.extend(releases)
        lines.append("    ),")

    out = Path(out_path)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(
        "// Generováno .github/scripts/release_notes.py z release-notes/ — needituj ručně.\n"
        "package eu.tintera.plume.core.changelog\n"
        "\n"
        "internal val GENERATED_CHANGELOG: Map<String, List<ChangelogRelease>> = mapOf(\n"
        + "\n".join(lines)
        + "\n)\n",
        encoding="utf-8",
    )
    print(f"bundle: {out} (vydání: {', '.join(counts)})")
    return 0


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

    if command == "exempt":
        # PR jen s komunitními překlady (Weblate) poznámku nepotřebuje: uživatel pozná nanejvýš
        # přeloženou větu navíc a Weblate poznámku napsat neumí. Nový jazyk, který tím přejde
        # práh pro výběr jazyků, si poznámku zaslouží — tu přidá ten, kdo vydává.
        files = [f for f in argv[2:] if f]
        if files and all(community_translation(f) for f in files):
            print("PR mění jen komunitní překlady — poznámka k vydání není potřeba.")
            return 0
        return 1

    if command == "play" and len(argv) in (5, 6):
        # Play = Android a hodinky, každé zvlášť: poznámka jen pro desktop nebo iOS tam nepatří
        # a do „Co je nového" na hodinkách jen ta, která je jmenuje.
        unit = argv[5] if len(argv) == 6 else "android"
        if unit not in PLAY_UNITS:
            print(f"neznámá platforma pro Play „{unit}“ — čekám {', '.join(PLAY_UNITS)}", file=sys.stderr)
            return 2
        notes = notes_between(argv[2], argv[3], unit)
        out = Path(argv[4])
        out.mkdir(parents=True, exist_ok=True)
        for lang in PLAY_LANGS:
            locale = LOCALES[lang][0]
            text = play_text(notes, lang)
            (out / f"whatsnew-{locale}").write_text(text, encoding="utf-8")
            print(f"--- whatsnew-{locale} ({len(text)}/{PLAY_LIMIT}) ---\n{text}")
        return 0

    if command == "markdown" and len(argv) in (4, 5):
        # Kanál vydání (`android`, `desktop`) vybere poznámky jeho platformy; bez něj všechny.
        # `wear` kanál nemá (vychází s tagem Androidu), ale do popisu release jde zvlášť.
        channel = argv[4] if len(argv) == 5 else None
        if channel and channel not in CHANNELS and channel not in PLAY_UNITS:
            print(f"neznámý kanál „{channel}“ — čekám {', '.join(dict.fromkeys([*CHANNELS, *PLAY_UNITS]))}", file=sys.stderr)
            return 2
        notes = notes_between(argv[2], argv[3], channel)
        # Do GitHub Release jde celý seznam, bez ořezu na limit Play.
        for lang, title in (("cs", "Co je nového"), ("en", "What's new")):
            print(f"### {title}\n")
            print("\n".join(f"- {n[lang]}" for n in notes) or f"_{LOCALES[lang][2]}_")
            print()
        return 0

    if command == "bundle" and len(argv) == 3:
        return bundle(argv[2])

    print(__doc__, file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv))
