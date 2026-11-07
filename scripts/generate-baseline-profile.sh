#!/usr/bin/env bash
set -euo pipefail

case "${1:-}" in
  composeApp|wearApp) app="$1" ;;
  *) echo "Usage: $0 {composeApp|wearApp}" >&2; exit 2 ;;
esac

profile_dir="$app/src/release/generated/baselineProfiles"
# Do not accept stale files if instrumentation silently fails or runs no tests.
rm -rf "$profile_dir"
if ! ./gradlew ":$app:generateBaselineProfile" --no-build-cache --stacktrace; then
  # The HTML report only reaches an artifact, so put why each test failed, and any crash on the
  # device, in the job log.
  echo "::group::Failed instrumentation tests"
  find . -path '*BaselineProfile/build/outputs/androidTest-results/*' -name '*.xml' \
    -exec grep -l '<failure' {} + | while read -r result; do
    echo "== $result"
    sed -n '/<failure/,/<\/failure>/p' "$result" | head -80
  done
  echo "::endgroup::"
  echo "::group::Device crash log"
  adb logcat -d -b crash || true
  adb logcat -d -s AndroidRuntime:E TestRunner:* || true
  echo "::endgroup::"
  exit 1
fi
for file in baseline-prof.txt startup-prof.txt; do
  if [[ ! -s "$profile_dir/$file" ]]; then
    echo "::error::Missing or empty $profile_dir/$file; inspect instrumentation results" >&2
    exit 1
  fi
done
