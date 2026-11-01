#!/usr/bin/env bash
set -euo pipefail

case "${1:-}" in
  composeApp|wearApp) app="$1" ;;
  *) echo "Usage: $0 {composeApp|wearApp}" >&2; exit 2 ;;
esac

profile_dir="$app/src/release/generated/baselineProfiles"
# Do not accept stale files if instrumentation silently fails or runs no tests.
rm -rf "$profile_dir"
./gradlew ":$app:generateBaselineProfile" --no-build-cache --stacktrace
for file in baseline-prof.txt startup-prof.txt; do
  if [[ ! -s "$profile_dir/$file" ]]; then
    echo "::error::Missing or empty $profile_dir/$file; inspect instrumentation results" >&2
    exit 1
  fi
done
