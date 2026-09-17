#!/usr/bin/env bash
#
# Vypíše sekci „Změny od <PREV>" pro release notes — proklikávatelnou.
#
# Každá položka odkazuje na PR, přes který změna přišla; když PR není (commit
# rovnou na main), odkazuje na commit. Hash commitu zůstává u obou variant,
# taky jako odkaz.
#
#   ./release-notes.sh <předchozí-tag> <tag> [flavoru|targetu]
#
# Prázdný <předchozí-tag> = první release, vypíše se jen poznámka.
# Repozitář a base URL bere z GITHUB_REPOSITORY / GITHUB_SERVER_URL.

set -euo pipefail

PREV="${1:-}"
TAG="${2:-${GITHUB_REF_NAME:-HEAD}}"
WHAT="${3:-flavoru}"
REPO="${GITHUB_REPOSITORY:?GITHUB_REPOSITORY není nastavené}"
SERVER="${GITHUB_SERVER_URL:-https://github.com}"

if [ -z "$PREV" ]; then
  echo "_První release tohoto ${WHAT}._"
  exit 0
fi

echo "### Změny od [\`${PREV}\`](${SERVER}/${REPO}/releases/tag/${PREV})"
echo

# --first-parent: po hlavní větvi. Každý PR je tak právě jeden (merge) commit
# a commity uvnitř PR se nevypisují znovu jednotlivě.
while IFS=$'\x1f' read -r -d $'\x1e' sha parents subject body; do
  sha="${sha#$'\n'}"
  [ -n "$sha" ] || continue

  short="${sha:0:8}"
  commit_link="[\`${short}\`](${SERVER}/${REPO}/commit/${sha})"

  pr=""
  title="$subject"

  if [[ "$subject" =~ ^Merge\ pull\ request\ \#([0-9]+) ]]; then
    # Klasický merge commit: v subjectu je číslo PR, v těle jeho název.
    pr="${BASH_REMATCH[1]}"
    title=$(printf '%s\n' "$body" | grep -m1 -v '^[[:space:]]*$' || true)
    [ -n "$title" ] || title="$subject"
  elif [[ "$subject" =~ ^(.*)\ \(\#([0-9]+)\)$ ]]; then
    # Squash merge: „Název PR (#123)".
    title="${BASH_REMATCH[1]}"
    pr="${BASH_REMATCH[2]}"
  elif [[ "$parents" == *" "* ]]; then
    # Merge bez PR (slití main do větve apod.) — do changelogu nepatří.
    continue
  fi

  if [ -n "$pr" ]; then
    echo "- ${title} — [#${pr}](${SERVER}/${REPO}/pull/${pr}) (${commit_link})"
  else
    echo "- ${title} — ${commit_link}"
  fi
done < <(git log --first-parent --pretty=$'%H\x1f%P\x1f%s\x1f%b\x1e' "${PREV}..${TAG}")

echo
echo "**Všechny commity:** [\`${PREV}\` → \`${TAG}\`](${SERVER}/${REPO}/compare/${PREV}...${TAG})"
