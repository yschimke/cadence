#!/usr/bin/env bash
# Wires this checkout's git hooks at .githooks/ so the pre-commit ktfmt
# cleanup runs before every commit. Idempotent.
set -euo pipefail

cd "${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel)}"

if [ ! -d .git ]; then
  exit 0
fi

git config core.hooksPath .githooks
