# Agent notes

- Develop on `agent/...` branches, never `claude/...`/`copilot/...`/etc.
  Enforced by `.github/workflows/no-ai-coauthors.yml`.
- Strip `Co-authored-by` trailers, AI bot emails, and "Generated with"
  lines from commits and PR bodies before pushing.
- Project layout: Compose Multiplatform app in `composeApp/`,
  pure-Kotlin library in `shared/`. Builds with
  `./gradlew :composeApp:assembleDebug`.
- Format Kotlin via `./gradlew ktfmtFormat`. Pre-commit hook installs
  with `./gradlew installGitHooks`.
