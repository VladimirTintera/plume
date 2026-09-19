"""Nahraje záznam aplikace v Google Play z adresáře `play/`.

    play_listing.py <service-account.json> [--dry-run]

Struktura:
    play/listing/<locale>/title.txt              – nejvýš 30 znaků
    play/listing/<locale>/short_description.txt  – nejvýš 80 znaků
    play/listing/<locale>/full_description.txt   – nejvýš 4000 znaků
    play/graphics/<locale>/<typ>/*.png           – obrázky pro jazyk; typ = icon, featureGraphic,
                                                   phoneScreenshots, sevenInchScreenshots,
                                                   tenInchScreenshots
    play/graphics/<typ>/*.png                    – společné pro všechny jazyky ze `listing/`

Obrázky jednoho typu se NAHRAZUJÍ celé (deleteall + upload v abecedním pořadí), takže pořadí
snímků v obchodě je pořadí souborů. Typ, pro který adresář není, se nechá, jak je.

Všechno jde v jednom editu: s `--dry-run` se edit jen ověří (`edits:validate`) a zahodí,
jinak se commitne. Ověření chytí i to, co lokální kontroly ne (rozměry obrázků, jazyk).
"""

import json
import sys
from pathlib import Path

from google.auth.transport.requests import AuthorizedSession
from google.oauth2 import service_account

PACKAGE = "eu.tintera.plume"
API = f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PACKAGE}/edits"
UPLOAD = f"https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/{PACKAGE}/edits"
ROOT = Path(__file__).resolve().parents[2] / "play"
LIMITS = {"title": 30, "short_description": 80, "full_description": 4000}
IMAGE_TYPES = ("icon", "featureGraphic", "phoneScreenshots", "sevenInchScreenshots", "tenInchScreenshots")


def check(response):
    if not response.ok:
        raise SystemExit(f"{response.request.method} {response.url}\n{response.status_code} {response.text}")
    return response.json() if response.content else {}


def read_listing(locale_dir):
    texts = {}
    for name, limit in LIMITS.items():
        text = (locale_dir / f"{name}.txt").read_text(encoding="utf-8").strip()
        if not text:
            raise SystemExit(f"{locale_dir.name}/{name}.txt je prázdný")
        if len(text) > limit:
            raise SystemExit(f"{locale_dir.name}/{name}.txt má {len(text)} znaků, Play bere nejvýš {limit}")
        texts[name] = text
    return {
        "language": locale_dir.name,
        "title": texts["title"],
        "shortDescription": texts["short_description"],
        "fullDescription": texts["full_description"],
    }


def image_dirs(locale):
    """Adresáře obrázků pro jazyk: vlastní mají přednost před společnými."""
    for image_type in IMAGE_TYPES:
        for base in (ROOT / "graphics" / locale, ROOT / "graphics"):
            directory = base / image_type
            if directory.is_dir():
                yield image_type, sorted(directory.glob("*.png"))
                break


def main():
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    dry_run = "--dry-run" in sys.argv[2:]
    credentials = service_account.Credentials.from_service_account_file(
        sys.argv[1], scopes=["https://www.googleapis.com/auth/androidpublisher"]
    )
    session = AuthorizedSession(credentials)

    listings = [read_listing(d) for d in sorted((ROOT / "listing").iterdir()) if d.is_dir()]

    edit = check(session.post(API))["id"]
    try:
        for listing in listings:
            locale = listing["language"]
            check(session.put(f"{API}/{edit}/listings/{locale}", json=listing))
            print(f"{locale}: texty")
            for image_type, files in image_dirs(locale):
                check(session.delete(f"{API}/{edit}/listings/{locale}/{image_type}"))
                for file in files:
                    check(session.post(
                        f"{UPLOAD}/{edit}/listings/{locale}/{image_type}",
                        params={"uploadType": "media"},
                        data=file.read_bytes(),
                        headers={"Content-Type": "image/png"},
                    ))
                print(f"{locale}: {image_type} ({len(files)})")

        check(session.post(f"{API}/{edit}:validate"))
        if dry_run:
            print("Ověřeno, nic se nezměnilo (--dry-run).")
            return
        response = session.post(f"{API}/{edit}:commit")
        # S řízeným publikováním Play změny bez tohohle příznaku odmítne.
        if not response.ok and "changesNotSentForReview" in response.text:
            response = session.post(f"{API}/{edit}:commit", params={"changesNotSentForReview": "true"})
        check(response)
        edit = None
        print("Záznam aktualizovaný.")
    finally:
        if edit is not None:
            session.delete(f"{API}/{edit}")


if __name__ == "__main__":
    main()
