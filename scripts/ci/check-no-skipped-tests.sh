#!/usr/bin/env bash
# Fails when any test report contains a skipped test. Silent skips hide missing coverage.
set -euo pipefail
DIR="${1:?usage: check-no-skipped-tests.sh <reports-dir>}"
if [ ! -d "$DIR" ]; then
  echo "Report directory not found: $DIR"
  exit 1
fi
if grep -l "<skipped" "$DIR"/TEST-*.xml 2>/dev/null; then
  echo "Skipped tests detected. Remove the cause instead of skipping."
  exit 1
fi
echo "No skipped tests."
