#!/usr/bin/env bash
# Podepíše balíčky desktopu pro self-update (Ed25519), vedle každého zapíše `<soubor>.sig`.
#
#   DESKTOP_UPDATE_SIGNING_KEY=<PEM soukromého klíče> sign-desktop-update.sh <tag> <soubor>...
#
# Podepisuje se text, ne soubor: `plume-desktop-update-v1\n<tag>\n<jméno souboru>\n<sha256>\n`.
# Verze a jméno v něm drží podpis u jednoho balíčku jedné verze — přenést ho na starší
# vydání nebo na balíček pro jiný systém nejde. Aplikace skládá tentýž text
# (`UpdateSignature.message` v client/shared/src/jvmMain/…/core/update/UpdateSignature.kt);
# `.sig` je podpis v base64 na jednom řádku.
#
# Skript odmítne podepsat, když klíč nesedí na veřejný klíč zabudovaný v aplikaci: vydání
# podepsané jiným klíčem by aplikace tiše ignorovala a nikdo by aktualizaci nedostal. Každý
# podpis si po sobě ověří. Viz docs/poznatky/desktop.md, „Podpis aktualizací".
set -euo pipefail

TAG="${1:?tag (desktop-X.Y.Z)}"
shift
[ "$#" -gt 0 ] || { echo "Chybí soubory k podpisu." >&2; exit 1; }
if [ -z "${DESKTOP_UPDATE_SIGNING_KEY:-}" ]; then
  echo "::error::Chybí secret DESKTOP_UPDATE_SIGNING_KEY (environment release) — bez podpisu aktualizaci žádná aplikace nenabídne." >&2
  exit 1
fi

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SOURCE="$ROOT/client/shared/src/jvmMain/kotlin/eu/tintera/plume/core/update/UpdateSignature.kt"
EXPECTED=$(sed -n 's/.*UPDATE_PUBLIC_KEY = "\([A-Za-z0-9+/=]*\)".*/\1/p' "$SOURCE")
[ -n "$EXPECTED" ] || { echo "::error::V $SOURCE není UPDATE_PUBLIC_KEY." >&2; exit 1; }

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
umask 077
printf '%s\n' "$DESKTOP_UPDATE_SIGNING_KEY" > "$WORK/key.pem"
openssl pkey -in "$WORK/key.pem" -pubout -out "$WORK/pub.pem"
ACTUAL=$(openssl pkey -pubin -in "$WORK/pub.pem" -outform DER | base64 | tr -d '\n')
if [ "$ACTUAL" != "$EXPECTED" ]; then
  echo "::error::Klíč DESKTOP_UPDATE_SIGNING_KEY nesedí na UPDATE_PUBLIC_KEY v aplikaci." >&2
  exit 1
fi

for FILE in "$@"; do
  NAME=$(basename "$FILE")
  SHA=$( (sha256sum "$FILE" 2>/dev/null || shasum -a 256 "$FILE") | cut -d' ' -f1)
  printf 'plume-desktop-update-v1\n%s\n%s\n%s\n' "$TAG" "$NAME" "$SHA" > "$WORK/message"
  openssl pkeyutl -sign -inkey "$WORK/key.pem" -rawin -in "$WORK/message" -out "$WORK/signature"
  openssl pkeyutl -verify -pubin -inkey "$WORK/pub.pem" -rawin -in "$WORK/message" -sigfile "$WORK/signature" > /dev/null
  base64 < "$WORK/signature" | tr -d '\n' > "$FILE.sig"
  echo "Podepsáno: $NAME ($SHA)"
done
