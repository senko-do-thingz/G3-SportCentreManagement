#!/usr/bin/env bash
# Guards Flyway migrations:
# 1. Already merged migrations must never be modified, renamed or deleted.
# 2. Version numbers must be unique across all migration folders.
# 3. New migrations must have a version greater than the highest version on the base branch.
set -euo pipefail

BASE_REF="${1:?usage: check-migrations.sh <base-ref>}"
MIG_DIRS=("backend/src/main/resources/db/migration" "backend/src/main/resources/db/demo")
status=0

changed="$(git diff --name-status "$BASE_REF"...HEAD -- "${MIG_DIRS[@]}" | awk '$1 != "A"' || true)"
if [ -n "$changed" ]; then
  echo "Existing migrations were modified, renamed or deleted:"
  echo "$changed"
  status=1
fi

versions_of() {
  sed -E 's#.*/V([0-9]+(\.[0-9]+)*)__.*#\1#'
}

dups="$(find "${MIG_DIRS[@]}" -type f -name 'V*__*.sql' | versions_of | sort | uniq -d || true)"
if [ -n "$dups" ]; then
  echo "Duplicate migration versions:"
  echo "$dups"
  status=1
fi

base_max="$(git ls-tree -r --name-only "$BASE_REF" -- "${MIG_DIRS[@]}" | grep -E '/V[0-9].*__.*\.sql$' | versions_of | sort -V | tail -n 1 || true)"
new_files="$(git diff --name-only --diff-filter=A "$BASE_REF"...HEAD -- "${MIG_DIRS[@]}" | grep -E '/V[0-9].*__.*\.sql$' || true)"
for f in $new_files; do
  v="$(echo "$f" | versions_of)"
  if [ -n "$base_max" ] && { [ "$v" = "$base_max" ] || [ "$(printf '%s\n%s\n' "$base_max" "$v" | sort -V | tail -n 1)" != "$v" ]; }; then
    echo "New migration $f has version $v which is not greater than base max $base_max"
    status=1
  fi
done

exit "$status"
