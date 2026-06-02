#!/usr/bin/env bash
set -euo pipefail

KTLINT_VERSION="1.8.0"
KTLINT_CACHE="${XDG_CACHE_HOME:-$HOME/.cache}/ktlint"
KTLINT_BIN="$KTLINT_CACHE/ktlint-$KTLINT_VERSION"

if [ ! -x "$KTLINT_BIN" ]; then
  mkdir -p "$KTLINT_CACHE"
  echo "  ⬇ downloading ktlint $KTLINT_VERSION …"
  curl -fsSL "https://github.com/ktlint/ktlint/releases/download/$KTLINT_VERSION/ktlint" \
    -o "$KTLINT_BIN"
  chmod +x "$KTLINT_BIN"
fi

cd "$(git rev-parse --show-toplevel)"

# Only lint staged .kt / .kts files under android/
staged=$(git diff --cached --name-only --diff-filter=ACM android/ | grep -E '\.kts?$' || true)
if [ -z "$staged" ]; then
  exit 0
fi

echo "$staged" | tr '\n' '\0' | xargs -0 "$KTLINT_BIN" --relative --color
