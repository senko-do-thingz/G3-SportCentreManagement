#!/usr/bin/env bash
# Fails when added lines contain forbidden characters (project convention).
# - Em dash, en dash, arrow glyphs, emoji and pictographs, check marks.
# - Vietnamese diacritics inside added Java lines (code and comments must be English).
set -euo pipefail
export LC_ALL=C.UTF-8

BASE="${1:-}"
if [ -z "$BASE" ] || [ "$BASE" = "0000000000000000000000000000000000000000" ]; then
  BASE="$(git rev-parse HEAD~1 2>/dev/null || git rev-parse HEAD)"
fi

SYMBOLS='[\x{2013}\x{2014}\x{2190}-\x{21FF}\x{2600}-\x{27BF}\x{1F000}-\x{1FAFF}]'
DIACRITICS='[\x{00C0}-\x{1EF9}]'
status=0

added_lines() {
  git diff -U0 --diff-filter=AM "$BASE" HEAD -- "$@" | grep -E '^\+' | grep -vE '^\+\+\+' || true
}

hits="$(added_lines '*.java' '*.sql' '*.yml' '*.yaml' '*.properties' '*.md' '*.xml' '*.sh' '*.ps1' | grep -nP "$SYMBOLS" || true)"
if [ -n "$hits" ]; then
  echo "Forbidden symbol characters found in added lines:"
  echo "$hits"
  status=1
fi

hits="$(added_lines '*.java' | grep -nP "$DIACRITICS" || true)"
if [ -n "$hits" ]; then
  echo "Non English characters found in added Java lines:"
  echo "$hits"
  status=1
fi

exit "$status"
