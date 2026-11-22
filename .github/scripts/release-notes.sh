#!/usr/bin/env bash
#
# Vypíše sekci „Changes since <PREV>" pro release notes — proklikávatelnou, anglicky
# (text GitHub Release je anglicky, česky jsou jen české poznámky pro uživatele).
#
# Každá položka odkazuje na PR, přes který změna přišla; když PR není (commit
# rovnou na main), odkazuje na commit. Hash commitu zůstává u obou variant,
# taky jako odkaz.
#
#   ./release-notes.sh <předchozí-tag> <tag> [target]
#
# Prázdný <předchozí-tag> = první release, vypíše se jen poznámka.
# Repozitář a base URL bere z GITHUB_REPOSITORY / GITHUB_SERVER_URL.
#
# PR sloučené před zveřejněním (do ARCHIVE_TIP, merge #1232 starého repa) mají čísla
# z archivu VladimirTintera/telegram — odkaz na ně míří tam, ne na PR téhož čísla v plume.

set -euo pipefail

PREV="${1:-}"
TAG="${2:-${GITHUB_REF_NAME:-HEAD}}"
WHAT="${3:-target}"
REPO="${GITHUB_REPOSITORY:?GITHUB_REPOSITORY není nastavené}"
SERVER="${GITHUB_SERVER_URL:-https://github.com}"
ARCHIVE_REPO="VladimirTintera/telegram"
ARCHIVE_TIP="d5e267a371a3fb7651f21b3c0a0a6f39304a0662"

if [ -z "$PREV" ]; then
  echo "_First release of this ${WHAT}._"
  exit 0
fi

echo "### Changes since [\`${PREV}\`](${SERVER}/${REPO}/releases/tag/${PREV})"
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
    pr_repo="$REPO"
    if git cat-file -e "${ARCHIVE_TIP}^{commit}" 2>/dev/null \
      && git merge-base --is-ancestor "$sha" "$ARCHIVE_TIP"; then
      pr_repo="$ARCHIVE_REPO"
    fi
    if [ "$pr_repo" = "$REPO" ]; then pr_label="#${pr}"; else pr_label="${pr_repo}#${pr}"; fi
    echo "- ${title} — [${pr_label}](${SERVER}/${pr_repo}/pull/${pr}) (${commit_link})"
  else
    echo "- ${title} — ${commit_link}"
  fi
done < <(git log --first-parent --pretty=$'%H\x1f%P\x1f%s\x1f%b\x1e' "${PREV}..${TAG}")

echo
echo "**All commits:** [\`${PREV}\` → \`${TAG}\`](${SERVER}/${REPO}/compare/${PREV}...${TAG})"
